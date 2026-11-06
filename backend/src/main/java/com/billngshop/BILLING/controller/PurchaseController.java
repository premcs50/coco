package com.billngshop.BILLING.controller;

import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.PurchaseLine;
import com.billngshop.BILLING.entity.PurchaseOrder;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.entity.Supplier;
import com.billngshop.BILLING.repository.PurchaseOrderRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.repository.SupplierRepository;
import com.billngshop.BILLING.service.ApiDtos.PurchaseLineRequest;
import com.billngshop.BILLING.service.ApiDtos.PurchaseRequest;
import com.billngshop.BILLING.service.ApiDtos.ReceiveRequest;
import com.billngshop.BILLING.service.ApiException;
import com.billngshop.BILLING.service.AuditService;
import com.billngshop.BILLING.service.InventoryService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
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
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseOrderRepository purchases;
    private final SupplierRepository suppliers;
    private final StoreSettingRepository settings;
    private final InventoryService inventory;
    private final AuditService audit;

    public PurchaseController(PurchaseOrderRepository purchases, SupplierRepository suppliers,
            StoreSettingRepository settings, InventoryService inventory, AuditService audit) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.settings = settings;
        this.inventory = inventory;
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public PurchaseOrder create(@Valid @RequestBody PurchaseRequest request, Authentication authentication) {
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Supplier not found"));
        if (!supplier.isActive()) {
            throw ApiException.badRequest("Inactive suppliers cannot be used for purchases");
        }
        long distinctProducts = request.lines().stream().map(PurchaseLineRequest::productId).distinct().count();
        if (distinctProducts != request.lines().size()) {
            throw ApiException.badRequest("A product can appear only once in a purchase order");
        }
        PurchaseOrder purchase = new PurchaseOrder();
        purchase.setSupplierId(supplier.getId());
        purchase.setCreatedBy(authentication.getName());
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseLineRequest item : request.lines()) {
            Product product = inventory.requireProduct(item.productId());
            PurchaseLine line = new PurchaseLine();
            line.setPurchaseOrder(purchase);
            line.setProductId(product.getId());
            line.setOrderedQuantity(item.quantity());
            line.setReceivedQuantity(0);
            line.setReturnedQuantity(0);
            line.setUnitCost(item.unitCost());
            purchase.getLines().add(line);
            total = total.add(item.unitCost().multiply(BigDecimal.valueOf(item.quantity())));
        }
        purchase.setTotal(total);
        BigDecimal approvalThreshold = new BigDecimal(settings.findById("purchasing.approval-threshold")
                .map(StoreSetting::getSettingValue).orElse("1000"));
        purchase.setStatus(total.compareTo(approvalThreshold) >= 0
                ? PurchaseOrder.Status.PENDING_APPROVAL : PurchaseOrder.Status.APPROVED);
        purchase = purchases.save(purchase);
        audit.record("PURCHASE_CREATED", "PURCHASE", purchase.getId(), "Total " + total);
        return purchase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public List<PurchaseOrder> list() {
        return purchases.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public PurchaseOrder get(@PathVariable Long id) {
        return findPurchase(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Transactional
    public PurchaseOrder approve(@PathVariable Long id, Authentication authentication) {
        PurchaseOrder purchase = findPurchase(id);
        if (purchase.getStatus() != PurchaseOrder.Status.PENDING_APPROVAL) {
            throw ApiException.conflict("Purchase order is not awaiting approval");
        }
        purchase.setStatus(PurchaseOrder.Status.APPROVED);
        purchase = purchases.save(purchase);
        audit.record("PURCHASE_APPROVED", "PURCHASE", id, "Approved by " + authentication.getName());
        return purchase;
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public PurchaseOrder receive(@PathVariable Long id,
            @Valid @RequestBody List<@Valid ReceiveRequest> receipt, Authentication authentication) {
        if (receipt.isEmpty()) {
            throw ApiException.badRequest("At least one receipt line is required");
        }
        PurchaseOrder purchase = findPurchase(id);
        if (purchase.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL
                || purchase.getStatus() == PurchaseOrder.Status.RETURNED) {
            throw ApiException.conflict("Purchase order cannot be received in its current state");
        }
        for (ReceiveRequest received : receipt) {
            PurchaseLine line = findLine(purchase, received.productId());
            if (line.getReceivedQuantity() + received.quantity() > line.getOrderedQuantity()) {
                throw ApiException.conflict("Received quantity exceeds the amount ordered");
            }
            line.setReceivedQuantity(line.getReceivedQuantity() + received.quantity());
            inventory.changeStock(line.getProductId(), received.quantity(), "PURCHASE_RECEIPT",
                    "Purchase " + id + " received", authentication.getName());
        }
        updateStatus(purchase);
        purchase = purchases.save(purchase);
        audit.record("PURCHASE_RECEIVED", "PURCHASE", id, "Receipt lines " + receipt.size());
        return purchase;
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public PurchaseOrder returnToSupplier(@PathVariable Long id,
            @Valid @RequestBody List<@Valid ReceiveRequest> returns, Authentication authentication) {
        PurchaseOrder purchase = findPurchase(id);
        if (returns.isEmpty()) {
            throw ApiException.badRequest("At least one return line is required");
        }
        for (ReceiveRequest returned : returns) {
            PurchaseLine line = findLine(purchase, returned.productId());
            if (line.getReturnedQuantity() + returned.quantity() > line.getReceivedQuantity()) {
                throw ApiException.conflict("Supplier return exceeds quantity received");
            }
            line.setReturnedQuantity(line.getReturnedQuantity() + returned.quantity());
            inventory.changeStock(line.getProductId(), -returned.quantity(), "SUPPLIER_RETURN",
                    "Returned to supplier for purchase " + id, authentication.getName());
        }
        purchase.setStatus(PurchaseOrder.Status.RETURNED);
        purchase = purchases.save(purchase);
        audit.record("SUPPLIER_RETURN", "PURCHASE", id, "Return lines " + returns.size());
        return purchase;
    }

    private PurchaseLine findLine(PurchaseOrder purchase, Long productId) {
        return purchase.getLines().stream()
                .filter(line -> line.getProductId().equals(productId))
                .findFirst().orElseThrow(() -> ApiException.badRequest("Product is not part of this purchase order"));
    }

    private PurchaseOrder findPurchase(Long id) {
        return purchases.findById(id).orElseThrow(() -> ApiException.notFound("Purchase order not found"));
    }

    private void updateStatus(PurchaseOrder purchase) {
        boolean fullyReceived = purchase.getLines().stream()
                .allMatch(line -> line.getReceivedQuantity() == line.getOrderedQuantity());
        boolean anyReceived = purchase.getLines().stream().anyMatch(line -> line.getReceivedQuantity() > 0);
        purchase.setStatus(fullyReceived ? PurchaseOrder.Status.RECEIVED
                : anyReceived ? PurchaseOrder.Status.PARTIALLY_RECEIVED : PurchaseOrder.Status.APPROVED);
    }
}
