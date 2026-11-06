package com.billngshop.BILLING.controller;

import com.billngshop.BILLING.entity.Customer;
import com.billngshop.BILLING.entity.InvoiceLine;
import com.billngshop.BILLING.entity.Payment;
import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.RefundRecord;
import com.billngshop.BILLING.entity.SalesInvoice;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.repository.CustomerRepository;
import com.billngshop.BILLING.repository.RefundRecordRepository;
import com.billngshop.BILLING.repository.SalesInvoiceRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.service.ApiDtos.InvoiceLineRequest;
import com.billngshop.BILLING.service.ApiDtos.InvoiceRequest;
import com.billngshop.BILLING.service.ApiDtos.PaymentRequest;
import com.billngshop.BILLING.service.ApiDtos.ReturnRequest;
import com.billngshop.BILLING.service.ApiDtos.VoidRequest;
import com.billngshop.BILLING.service.ApiException;
import com.billngshop.BILLING.service.AuditService;
import com.billngshop.BILLING.service.InventoryService;
import com.billngshop.BILLING.service.SimplePdf;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class BillingController {
    private final SalesInvoiceRepository invoices;
    private final CustomerRepository customers;
    private final StoreSettingRepository settings;
    private final RefundRecordRepository refunds;
    private final InventoryService inventory;
    private final AuditService audit;

    public BillingController(SalesInvoiceRepository invoices, CustomerRepository customers,
            StoreSettingRepository settings, RefundRecordRepository refunds, InventoryService inventory,
            AuditService audit) {
        this.invoices = invoices;
        this.customers = customers;
        this.settings = settings;
        this.refunds = refunds;
        this.inventory = inventory;
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    @Transactional
    public SalesInvoice createInvoice(@Valid @RequestBody InvoiceRequest request, Authentication authentication) {
        return create(request, authentication, false);
    }

    @PostMapping("/hold")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    @Transactional
    public SalesInvoice holdInvoice(@Valid @RequestBody InvoiceRequest request, Authentication authentication) {
        return create(request, authentication, true);
    }

    @GetMapping("/held")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public List<SalesInvoice> heldInvoices() {
        return invoices.findAll().stream().filter(invoice -> invoice.getStatus() == SalesInvoice.Status.HELD).toList();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public List<SalesInvoice> listInvoices() {
        return invoices.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public SalesInvoice invoice(@PathVariable Long id) {
        return getInvoice(id);
    }

    @GetMapping(value = "/{id}/print", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public org.springframework.http.ResponseEntity<byte[]> printInvoice(@PathVariable Long id) {
        SalesInvoice invoice = getInvoice(id);
        StringBuilder text = new StringBuilder("Invoice: ").append(invoice.getInvoiceNumber())
                .append("\nDate: ").append(invoice.getCreatedAt())
                .append("\nStore: ").append(setting("store.name", "Retail Store"))
                .append("\nAddress: ").append(setting("store.address", ""))
                .append("\nPhone: ").append(setting("store.phone", ""))
                .append("\nCustomer: ").append(invoice.getCustomerId() == null ? "Walk-in"
                        : customers.findById(invoice.getCustomerId()).map(Customer::getName).orElse("Customer"))
                .append("\nCashier: ").append(invoice.getCashier()).append("\n\nItems:\n");
        invoice.getLines().forEach(line -> text.append(line.getProductName()).append(" x ")
                .append(line.getQuantity()).append(" @ ").append(line.getUnitPrice()).append(" = ")
                .append(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))).append('\n'));
        text.append("\nSubtotal: ").append(invoice.getSubtotal())
                .append("\nTax: ").append(invoice.getTax())
                .append("\nDiscount: ").append(invoice.getDiscount())
                .append("\nTotal: ").append(invoice.getTotal())
                .append("\nStatus: ").append(invoice.getStatus())
                .append("\n").append(setting("store.invoice-footer", "Thank you for shopping."));
        return org.springframework.http.ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                .body(SimplePdf.render("Retail invoice", text.toString()));
    }

    @PostMapping("/{id}/recall")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public SalesInvoice recall(@PathVariable Long id) {
        SalesInvoice invoice = getInvoice(id);
        if (invoice.getStatus() != SalesInvoice.Status.HELD) {
            throw ApiException.conflict("Only held invoices can be recalled");
        }
        return invoice;
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    @Transactional
    public SalesInvoice finalizeInvoice(@PathVariable Long id,
            @Valid @RequestBody List<@Valid PaymentRequest> paymentRequests, Authentication authentication) {
        SalesInvoice invoice = getInvoice(id);
        if (invoice.getStatus() != SalesInvoice.Status.HELD) {
            throw ApiException.conflict("Only held invoices can be finalized");
        }
        addPayments(invoice, paymentRequests);
        completeSale(invoice, authentication.getName());
        return invoices.save(invoice);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Transactional
    public SalesInvoice voidInvoice(@PathVariable Long id, @Valid @RequestBody VoidRequest request,
                                    Authentication authentication) {
        SalesInvoice invoice = getInvoice(id);
        if (invoice.getStatus() != SalesInvoice.Status.COMPLETED) {
            throw ApiException.conflict("Only completed invoices can be voided");
        }
        if (invoice.getLines().stream().anyMatch(line -> line.getReturnedQuantity() > 0)) {
            throw ApiException.conflict("An invoice with processed returns cannot be voided");
        }
        invoice.getLines().forEach(line -> inventory.changeStock(line.getProductId(), line.getQuantity(),
                "VOID_REVERSAL", "Invoice void: " + request.reason(), authentication.getName()));
        for (Payment payment : invoice.getPayments()) {
            createRefund(invoice.getId(), 0L, 0, payment.getAmount(), payment.getMode(), request.reason(),
                    authentication.getName());
        }
        invoice.setStatus(SalesInvoice.Status.VOIDED);
        invoice.setReason(request.reason());
        invoice = invoices.save(invoice);
        audit.record("INVOICE_VOIDED", "INVOICE", id, request.reason());
        return invoice;
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    @Transactional
    public SalesInvoice returnItems(@PathVariable Long id, @Valid @RequestBody ReturnRequest request,
                                    Authentication authentication) {
        SalesInvoice invoice = getInvoice(id);
        if (invoice.getStatus() != SalesInvoice.Status.COMPLETED) {
            throw ApiException.conflict("Returns require a completed invoice");
        }
        if (!isPaymentModeEnabled(request.refundMode())) {
            throw ApiException.badRequest("Refund payment mode is disabled: " + request.refundMode());
        }
        List<RefundRecord> createdRefunds = new ArrayList<>();
        BigDecimal refunded = BigDecimal.ZERO;
        for (InvoiceLineRequest item : request.lines()) {
            InvoiceLine line = invoice.getLines().stream()
                    .filter(candidate -> candidate.getProductId().equals(item.productId()))
                    .findFirst().orElseThrow(() -> ApiException.badRequest(
                            "Product " + item.productId() + " is not on this invoice"));
            int remaining = line.getQuantity() - line.getReturnedQuantity();
            if (item.quantity() > remaining) {
                throw ApiException.conflict("Return quantity exceeds the unreturned quantity");
            }
            BigDecimal amount = line.getUnitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                    .multiply(invoice.getTotal())
                    .divide(invoice.getSubtotal(), 2, RoundingMode.HALF_UP);
            refunded = refunded.add(amount);
            line.setReturnedQuantity(line.getReturnedQuantity() + item.quantity());
            inventory.changeStock(line.getProductId(), item.quantity(), "SALES_RETURN",
                    "Invoice return: " + request.reason(), authentication.getName());
            RefundRecord record = createRefund(id, line.getProductId(), item.quantity(), amount,
                    request.refundMode().toUpperCase(), request.reason(), authentication.getName());
            createdRefunds.add(record);
        }
        BigDecimal priorRefunds = refunds.findByInvoiceId(id).stream()
                .map(RefundRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (priorRefunds.compareTo(invoice.getTotal()) > 0) {
            throw ApiException.conflict("Refund amount exceeds invoice total");
        }
        if (invoice.getLines().stream().allMatch(line -> line.getReturnedQuantity() == line.getQuantity())) {
            invoice.setStatus(SalesInvoice.Status.RETURNED);
        }
        invoice = invoices.save(invoice);
        audit.record("INVOICE_RETURNED", "INVOICE", id,
                "Refunded " + refunded + " via " + request.refundMode() + "; records " + createdRefunds.size());
        return invoice;
    }

    private SalesInvoice create(InvoiceRequest request, Authentication authentication, boolean hold) {
        if (request.customerId() != null) {
            Customer customer = customers.findById(request.customerId())
                    .orElseThrow(() -> ApiException.badRequest("Customer not found"));
            if (!customer.isActive()) {
                throw ApiException.badRequest("Inactive customers cannot be added to new invoices");
            }
        }
        SalesInvoice invoice = new SalesInvoice();
        invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        invoice.setCustomerId(request.customerId());
        invoice.setCashier(authentication.getName());
        invoice.setTillId(request.tillId() == null || request.tillId().isBlank() ? "MAIN" : request.tillId());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (InvoiceLineRequest requestedLine : request.lines()) {
            Product product = inventory.requireProduct(requestedLine.productId());
            if (!product.isActive()) {
                throw ApiException.badRequest("Inactive products cannot be sold: " + product.getSku());
            }
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setProductId(product.getId());
            line.setProductName(product.getName());
            line.setQuantity(requestedLine.quantity());
            line.setUnitPrice(product.getSellingPrice());
            line.setUnitCost(product.getCostPrice());
            line.setReturnedQuantity(0);
            invoice.getLines().add(line);
            subtotal = subtotal.add(product.getSellingPrice().multiply(BigDecimal.valueOf(requestedLine.quantity())));
        }
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        BigDecimal maxDiscountPercent = new BigDecimal(setting("billing.max-discount-percent", "20"));
        BigDecimal discountLimit = subtotal.multiply(maxDiscountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (discount.compareTo(discountLimit) > 0 || discount.compareTo(subtotal) > 0) {
            throw ApiException.badRequest("Discount exceeds the configured limit");
        }
        boolean manager = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_STORE_MANAGER"));
        BigDecimal cashierMaxPercent = new BigDecimal(setting("billing.cashier-max-discount-percent", "10"));
        BigDecimal cashierLimit = subtotal.multiply(cashierMaxPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (!manager && discount.compareTo(cashierLimit) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Discount requires manager approval");
        }
        BigDecimal taxPercent = new BigDecimal(setting("billing.tax-rate-percent", "0"));
        BigDecimal tax = subtotal.subtract(discount).multiply(taxPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setDiscount(discount.setScale(2, RoundingMode.HALF_UP));
        invoice.setTax(tax);
        invoice.setTotal(subtotal.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP));
        if (invoice.getTotal().signum() <= 0) {
            throw ApiException.badRequest("Invoice total must be greater than zero");
        }
        invoice.setStatus(hold ? SalesInvoice.Status.HELD : SalesInvoice.Status.COMPLETED);
        if (!hold) {
            if (request.payments() == null || request.payments().isEmpty()) {
                throw ApiException.badRequest("At least one payment is required to complete an invoice");
            }
            addPayments(invoice, request.payments());
            completeSale(invoice, authentication.getName());
        }
        invoice = invoices.save(invoice);
        audit.record(hold ? "INVOICE_HELD" : "INVOICE_CREATED", "INVOICE", invoice.getId(),
                "Total " + invoice.getTotal());
        if (discount.signum() > 0) {
            audit.record(manager ? "DISCOUNT_APPROVED" : "DISCOUNT_APPLIED", "INVOICE", invoice.getId(),
                    discount + " discount applied by " + authentication.getName());
        }
        return invoice;
    }

    private void addPayments(SalesInvoice invoice, List<PaymentRequest> requests) {
        BigDecimal paid = requests.stream().map(PaymentRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(invoice.getTotal()) != 0) {
            throw ApiException.badRequest("Payment split must equal invoice total " + invoice.getTotal());
        }
        for (PaymentRequest request : requests) {
            if (!isPaymentModeEnabled(request.mode())) {
                throw ApiException.badRequest("Payment mode is disabled: " + request.mode());
            }
            Payment payment = new Payment();
            payment.setInvoice(invoice);
            payment.setMode(request.mode().toUpperCase());
            payment.setAmount(request.amount());
            invoice.getPayments().add(payment);
        }
    }

    private void completeSale(SalesInvoice invoice, String actor) {
        for (InvoiceLine line : invoice.getLines()) {
            inventory.changeStock(line.getProductId(), -line.getQuantity(), "SALE",
                    "Invoice " + invoice.getInvoiceNumber(), actor);
        }
        invoice.setStatus(SalesInvoice.Status.COMPLETED);
    }

    private RefundRecord createRefund(Long invoiceId, Long productId, int quantity, BigDecimal amount,
                                      String mode, String reason, String actor) {
        RefundRecord refund = new RefundRecord();
        refund.setInvoiceId(invoiceId);
        refund.setProductId(productId);
        refund.setQuantity(quantity);
        refund.setAmount(amount);
        refund.setPaymentMode(mode);
        refund.setReason(reason);
        refund.setProcessedBy(actor);
        return refunds.save(refund);
    }

    private boolean isPaymentModeEnabled(String mode) {
        return Boolean.parseBoolean(setting("payment." + mode.toLowerCase() + ".enabled", "true"));
    }

    private String setting(String key, String fallback) {
        return settings.findById(key).map(StoreSetting::getSettingValue).orElse(fallback);
    }

    private SalesInvoice getInvoice(Long id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }
}
