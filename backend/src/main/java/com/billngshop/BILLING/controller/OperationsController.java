package com.billngshop.BILLING.controller;

import com.billngshop.BILLING.entity.AuditEvent;
import com.billngshop.BILLING.entity.CashShift;
import com.billngshop.BILLING.entity.InvoiceLine;
import com.billngshop.BILLING.entity.Payment;
import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.RefundRecord;
import com.billngshop.BILLING.entity.SalesInvoice;
import com.billngshop.BILLING.entity.StoreExpense;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.repository.AuditEventRepository;
import com.billngshop.BILLING.repository.CashShiftRepository;
import com.billngshop.BILLING.repository.CustomerRepository;
import com.billngshop.BILLING.repository.ProductRepository;
import com.billngshop.BILLING.repository.RefundRecordRepository;
import com.billngshop.BILLING.repository.SalesInvoiceRepository;
import com.billngshop.BILLING.repository.StockMovementRepository;
import com.billngshop.BILLING.repository.StoreExpenseRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.service.ApiDtos.ExpenseRequest;
import com.billngshop.BILLING.service.ApiDtos.ReconcileRequest;
import com.billngshop.BILLING.service.ApiDtos.SettingRequest;
import com.billngshop.BILLING.service.ApiDtos.ShiftCloseRequest;
import com.billngshop.BILLING.service.ApiDtos.ShiftOpenRequest;
import com.billngshop.BILLING.service.ApiDtos.SalesTargetRequest;
import com.billngshop.BILLING.service.ApiException;
import com.billngshop.BILLING.service.AuditService;
import com.billngshop.BILLING.service.SimplePdf;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OperationsController {
    private final SalesInvoiceRepository invoices;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final RefundRecordRepository refunds;
    private final CashShiftRepository shifts;
    private final StoreExpenseRepository expenses;
    private final StoreSettingRepository settings;
    private final AuditEventRepository auditEvents;
    private final CustomerRepository customers;
    private final AuditService audit;

    public OperationsController(SalesInvoiceRepository invoices,
            ProductRepository products, StockMovementRepository movements,
            RefundRecordRepository refunds, CashShiftRepository shifts, StoreExpenseRepository expenses,
            StoreSettingRepository settings, AuditEventRepository auditEvents, CustomerRepository customers,
            AuditService audit) {
        this.invoices = invoices;
        this.products = products;
        this.movements = movements;
        this.refunds = refunds;
        this.shifts = shifts;
        this.expenses = expenses;
        this.settings = settings;
        this.auditEvents = auditEvents;
        this.customers = customers;
        this.audit = audit;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF','INVENTORY_STAFF')")
    public Map<String, Object> dashboard(Authentication authentication) {
        LocalDate today = LocalDate.now();
        List<SalesInvoice> todaysInvoices = invoicesInRange(today, today).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED
                        || invoice.getStatus() == SalesInvoice.Status.RETURNED).toList();
        BigDecimal sales = todaysInvoices.stream().map(this::netTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> topProducts = soldLines(todaysInvoices).stream()
                .collect(Collectors.groupingBy(InvoiceLine::getProductName,
                        Collectors.summingInt(line -> line.getQuantity() - line.getReturnedQuantity())))
                .entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .map(entry -> Map.<String, Object>of("product", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        return Map.of("date", today, "salesTotal", sales, "invoiceCount", todaysInvoices.size(),
                "lowStockCount", products.findByActiveTrue().stream()
                        .filter(product -> product.getStockQuantity() <= product.getReorderLevel()).count(),
                "topProducts", topProducts,
                "myShiftSales", todaysInvoices.stream().filter(invoice -> invoice.getCashier().equals(authentication.getName()))
                        .map(this::netTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @GetMapping("/reports/sales")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public Map<String, Object> salesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "date") String groupBy) {
        List<SalesInvoice> selected = invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED
                        || invoice.getStatus() == SalesInvoice.Status.RETURNED).toList();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (SalesInvoice invoice : selected) {
            String key = switch (groupBy.toLowerCase(Locale.ROOT)) {
                case "staff" -> invoice.getCashier();
                case "product" -> "product";
                case "category" -> "category";
                case "date" -> invoice.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().toString();
                default -> throw ApiException.badRequest("groupBy must be date, product, category, or staff");
            };
            if (groupBy.equalsIgnoreCase("product") || groupBy.equalsIgnoreCase("category")) {
                for (InvoiceLine line : invoice.getLines()) {
                    Product product = products.findById(line.getProductId()).orElse(null);
                    String itemKey = groupBy.equalsIgnoreCase("product") ? line.getProductName()
                            : product == null || product.getCategoryId() == null ? "Uncategorized"
                            : "Category " + product.getCategoryId();
                    totals.merge(itemKey, line.getUnitPrice().multiply(
                                    BigDecimal.valueOf(line.getQuantity() - line.getReturnedQuantity())),
                            BigDecimal::add);
                }
            } else {
                totals.merge(key, netTotal(invoice), BigDecimal::add);
            }
        }
        return Map.of("from", from, "to", to, "groupBy", groupBy, "total",
                selected.stream().map(this::netTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                "groups", totals);
    }

    @GetMapping("/reports/inventory")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Map<String, Object> inventoryReport() {
        List<Product> active = products.findByActiveTrue();
        return Map.of("products", active, "lowStockCount", active.stream()
                .filter(product -> product.getStockQuantity() <= product.getReorderLevel()).count(),
                "movementCount", movements.count());
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q) {
        String query = q.toLowerCase(Locale.ROOT);
        return Map.of("products", products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q, q),
                "customers", customers.findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(q, q).stream()
                        .filter(customer -> customer.isActive()).toList(),
                "invoices", invoices.findAll().stream()
                        .filter(invoice -> invoice.getInvoiceNumber().toLowerCase(Locale.ROOT).contains(query))
                        .limit(50).toList());
    }

    @GetMapping("/reports/sales/export")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<byte[]> exportSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format) {
        List<SalesInvoice> rows = invoicesInRange(from, to);
        String content = "invoiceNumber,date,cashier,status,total\n" + rows.stream()
                .map(invoice -> csv(invoice.getInvoiceNumber()) + ","
                        + invoice.getCreatedAt() + "," + csv(invoice.getCashier()) + ","
                        + invoice.getStatus() + "," + invoice.getTotal())
                .collect(Collectors.joining("\n"));
        return export(content, "sales-report", format);
    }

    @GetMapping("/reports/inventory/export")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<byte[]> exportInventory(@RequestParam(defaultValue = "csv") String format) {
        String content = "sku,name,stock,reorderLevel,costPrice,sellingPrice\n" + products.findAll().stream()
                .map(product -> csv(product.getSku()) + "," + csv(product.getName()) + ","
                        + product.getStockQuantity() + "," + product.getReorderLevel() + ","
                        + product.getCostPrice() + "," + product.getSellingPrice())
                .collect(Collectors.joining("\n"));
        return export(content, "inventory-report", format);
    }

    @GetMapping("/reports/compare")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> comparePeriods(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstTo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate secondFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate secondTo) {
        BigDecimal first = revenue(firstFrom, firstTo);
        BigDecimal second = revenue(secondFrom, secondTo);
        return Map.of("firstPeriod", Map.of("from", firstFrom, "to", firstTo, "sales", first),
                "secondPeriod", Map.of("from", secondFrom, "to", secondTo, "sales", second),
                "difference", second.subtract(first));
    }

    @GetMapping("/analytics/sales-trends")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> salesTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String period) {
        Map<String, BigDecimal> trends = new LinkedHashMap<>();
        for (SalesInvoice invoice : invoicesInRange(from, to)) {
            if (invoice.getStatus() != SalesInvoice.Status.COMPLETED
                    && invoice.getStatus() != SalesInvoice.Status.RETURNED) {
                continue;
            }
            LocalDate date = invoice.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            String key = switch (period.toLowerCase(Locale.ROOT)) {
                case "day", "daily" -> date.toString();
                case "week", "weekly" -> date.with(java.time.DayOfWeek.MONDAY).toString();
                case "month", "monthly" -> date.withDayOfMonth(1).toString();
                default -> throw ApiException.badRequest("period must be day, week, or month");
            };
            trends.merge(key, netTotal(invoice), BigDecimal::add);
        }
        return Map.of("from", from, "to", to, "period", period, "sales", trends);
    }

    @GetMapping("/analytics/performance")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<SalesInvoice> rows = invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED).toList();
        Map<String, BigDecimal> byStaff = rows.stream().collect(Collectors.groupingBy(SalesInvoice::getCashier,
                Collectors.mapping(this::netTotal, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<Long, BigDecimal> byCategory = new LinkedHashMap<>();
        for (InvoiceLine line : soldLines(rows)) {
            products.findById(line.getProductId()).map(Product::getCategoryId)
                    .ifPresent(category -> byCategory.merge(category,
                            line.getUnitPrice().multiply(
                                    BigDecimal.valueOf(line.getQuantity() - line.getReturnedQuantity())),
                            BigDecimal::add));
        }
        return Map.of("byStaff", byStaff, "byCategory", byCategory);
    }

    @GetMapping("/analytics/profit-margin")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> profitMargin(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<SalesInvoice> rows = invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED).toList();
        BigDecimal revenue = rows.stream().map(this::netTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = soldLines(rows).stream().map(line -> line.getUnitCost()
                .multiply(BigDecimal.valueOf(line.getQuantity() - line.getReturnedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = revenue.subtract(cost);
        BigDecimal margin = revenue.signum() == 0 ? BigDecimal.ZERO
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);
        return Map.of("revenue", revenue, "cost", cost, "profit", profit, "marginPercent", margin);
    }

    @GetMapping("/analytics/peak-hours")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> peakHours(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<Integer, Long> counts = invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED)
                .collect(Collectors.groupingBy(invoice -> invoice.getCreatedAt().atZone(ZoneId.systemDefault()).getHour(),
                        Collectors.counting()));
        return Map.of("byHour", counts, "byDayOfWeek", invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED)
                .collect(Collectors.groupingBy(invoice -> invoice.getCreatedAt().atZone(ZoneId.systemDefault())
                        .getDayOfWeek().toString(), Collectors.counting())));
    }

    @GetMapping("/analytics/abc")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public List<Map<String, Object>> abcAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        for (InvoiceLine line : soldLines(invoicesInRange(from, to))) {
            values.merge(line.getProductId(), line.getUnitPrice().multiply(
                            BigDecimal.valueOf(line.getQuantity() - line.getReturnedQuantity())),
                    BigDecimal::add);
        }
        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map.Entry<Long, BigDecimal>> sorted = values.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()).toList();
        BigDecimal cumulative = BigDecimal.ZERO;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> row : sorted) {
            cumulative = cumulative.add(row.getValue());
            BigDecimal percent = total.signum() == 0 ? BigDecimal.ZERO
                    : cumulative.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            Product product = products.findById(row.getKey()).orElse(null);
            String tier = percent.compareTo(BigDecimal.valueOf(80)) <= 0 ? "A"
                    : percent.compareTo(BigDecimal.valueOf(95)) <= 0 ? "B" : "C";
            result.add(Map.of("productId", row.getKey(), "name", product == null ? "Unknown" : product.getName(),
                    "salesValue", row.getValue(), "cumulativePercent", percent, "class", tier));
        }
        return result;
    }

    @GetMapping("/analytics/dead-stock")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public List<Product> deadStock(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var sold = soldLines(invoicesInRange(from, to)).stream()
                .map(InvoiceLine::getProductId).collect(Collectors.toSet());
        return products.findByActiveTrue().stream().filter(product -> !sold.contains(product.getId())).toList();
    }

    @PostMapping("/analytics/targets")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public StoreSetting setSalesTarget(@Valid @RequestBody SalesTargetRequest request) {
        String key = "sales-target." + request.date();
        StoreSetting setting = settings.findById(key).orElseGet(StoreSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(request.amount().toPlainString());
        return settings.save(setting);
    }

    @GetMapping("/analytics/targets")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> salesTarget(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        BigDecimal target = new BigDecimal(settings.findById("sales-target." + date)
                .map(StoreSetting::getSettingValue).orElse("0"));
        BigDecimal actual = revenue(date, date);
        return Map.of("date", date, "target", target, "actual", actual, "variance", actual.subtract(target));
    }

    @PostMapping("/shifts")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    @Transactional
    public CashShift openShift(@Valid @RequestBody ShiftOpenRequest request, Authentication authentication) {
        if (shifts.findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(authentication.getName()).isPresent()) {
            throw ApiException.conflict("Close your current shift before opening another");
        }
        CashShift shift = new CashShift();
        shift.setUsername(authentication.getName());
        shift.setTillId(request.tillId());
        shift.setOpeningCash(request.openingCash());
        shift = shifts.save(shift);
        audit.record("SHIFT_OPENED", "SHIFT", shift.getId(), "Till " + shift.getTillId());
        return shift;
    }

    @GetMapping("/shifts/current")
    public CashShift currentShift(Authentication authentication) {
        return shifts.findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(authentication.getName())
                .orElseThrow(() -> ApiException.notFound("No open shift"));
    }

    @GetMapping("/shifts")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public List<CashShift> listShifts() {
        return shifts.findAll();
    }

    @PostMapping("/shifts/{id}/close")
    @Transactional
    public Map<String, Object> closeShift(@PathVariable Long id,
            @Valid @RequestBody ShiftCloseRequest request, Authentication authentication) {
        CashShift shift = shifts.findById(id).orElseThrow(() -> ApiException.notFound("Shift not found"));
        if (!shift.getUsername().equals(authentication.getName())
                && !authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_STORE_MANAGER"))) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "You cannot close this shift");
        }
        if (shift.getClosedAt() != null) {
            throw ApiException.conflict("Shift is already closed");
        }
        shift.setClosingCash(request.closingCash());
        shift.setClosedAt(Instant.now());
        shift = shifts.save(shift);
        Map<String, Object> reconciliation = reconcile(shift.getOpenedAt().atZone(ZoneId.systemDefault())
                .toLocalDate(), shift.getTillId(), request.closingCash(), shift.getOpeningCash());
        audit.record("SHIFT_CLOSED", "SHIFT", id, "Closing cash " + request.closingCash());
        return Map.of("shift", shift, "reconciliation", reconciliation);
    }

    @PostMapping("/expenses")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Transactional
    public StoreExpense recordExpense(@Valid @RequestBody ExpenseRequest request, Authentication authentication) {
        StoreExpense expense = new StoreExpense();
        expense.setAmount(request.amount());
        expense.setPaymentMode(request.paymentMode().toUpperCase());
        expense.setTillId(request.tillId());
        expense.setDescription(request.description());
        expense.setRecordedBy(authentication.getName());
        Long shiftId = shifts.findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(authentication.getName())
                .filter(shift -> shift.getTillId().equals(request.tillId()))
                .map(CashShift::getId).orElse(null);
        expense.setShiftId(shiftId);
        expense = expenses.save(expense);
        audit.record("EXPENSE_RECORDED", "EXPENSE", expense.getId(), expense.getDescription());
        return expense;
    }

    @GetMapping("/payments/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, BigDecimal> paymentModeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (SalesInvoice invoice : invoicesInRange(date, date)) {
            if (invoice.getStatus() != SalesInvoice.Status.COMPLETED
                    && invoice.getStatus() != SalesInvoice.Status.RETURNED) {
                continue;
            }
            for (Payment payment : invoice.getPayments()) {
                totals.merge(payment.getMode(), payment.getAmount(), BigDecimal::add);
            }
        }
        for (RefundRecord refund : refunds.findAll()) {
            if (refund.getProcessedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(date)) {
                totals.merge(refund.getPaymentMode(), refund.getAmount().negate(), BigDecimal::add);
            }
        }
        return totals;
    }

    @PostMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> reconcileDay(@Valid @RequestBody ReconcileRequest request) {
        return reconcile(request.date(), request.tillId(), request.declaredCash(), BigDecimal.ZERO);
    }

    @PutMapping("/settings/tax")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSetting configureTax(@Valid @RequestBody SettingRequest request) {
        return setNumericSetting("billing.tax-rate-percent", request.value(), BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @PutMapping("/settings/discount-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSetting configureDiscount(@Valid @RequestBody SettingRequest request) {
        return setNumericSetting("billing.max-discount-percent", request.value(), BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    @PutMapping("/settings/store-profile")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StoreSetting> configureStoreProfile(@RequestBody Map<String, String> values) {
        if (values == null || values.keySet().stream().anyMatch(key -> !key.matches("[a-zA-Z0-9._-]{1,60}"))) {
            throw ApiException.badRequest("Store profile fields have invalid keys");
        }
        return values.entrySet().stream().map(entry -> saveSetting("store." + entry.getKey(), entry.getValue())).toList();
    }

    @PutMapping("/settings/payment-modes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StoreSetting> configurePaymentModes(@RequestBody Map<String, Boolean> modes) {
        if (modes == null || modes.keySet().stream().anyMatch(key -> !List.of("cash", "card", "upi").contains(key))) {
            throw ApiException.badRequest("Only cash, card, and upi payment modes are supported");
        }
        List<StoreSetting> saved = new ArrayList<>();
        for (String mode : List.of("cash", "card", "upi")) {
            Boolean enabled = modes.get(mode);
            if (enabled != null) {
                saved.add(saveSetting("payment." + mode + ".enabled", enabled.toString()));
            }
        }
        return saved;
    }

    @PutMapping("/settings/session-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StoreSetting> configureSessionPolicy(@RequestBody Map<String, String> policy) {
        if (policy == null || policy.keySet().stream().anyMatch(key -> !List.of("session-timeout-minutes",
                "maximum-concurrent-sessions", "login-failure-threshold", "login-lock-minutes").contains(key))) {
            throw ApiException.badRequest("Unsupported session policy setting");
        }
        List<StoreSetting> saved = new ArrayList<>();
        for (String key : List.of("session-timeout-minutes", "maximum-concurrent-sessions",
                "login-failure-threshold", "login-lock-minutes")) {
            if (policy.containsKey(key)) {
                long value;
                try {
                    value = Long.parseLong(policy.get(key));
                } catch (NumberFormatException exception) {
                    throw ApiException.badRequest(key + " must be an integer");
                }
                long minimum = key.equals("maximum-concurrent-sessions") ? 0 : 1;
                long maximum = key.equals("maximum-concurrent-sessions") ? 20
                        : key.equals("login-failure-threshold") ? 20 : 1440;
                if (value < minimum || value > maximum
                        || key.equals("login-failure-threshold") && value < 3) {
                    throw ApiException.badRequest(key + " is outside the allowed range");
                }
                saved.add(saveSetting("security." + key, Long.toString(value)));
            }
        }
        return saved;
    }

    @PutMapping("/notifications/preferences")
    public List<StoreSetting> notificationPreferences(Authentication authentication,
                                                       @RequestBody Map<String, Boolean> preferences) {
        if (preferences == null || preferences.entrySet().stream().anyMatch(entry ->
                !List.of("lowStock.enabled", "approvals.enabled", "events.enabled").contains(entry.getKey())
                        || entry.getValue() == null)) {
            throw ApiException.badRequest("Unsupported notification preference");
        }
        return preferences.entrySet().stream().map(entry -> saveSetting(
                "notifications." + authentication.getName() + "." + entry.getKey(), entry.getValue().toString()))
                .toList();
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEvent> auditLog() {
        return auditEvents.findAllByOrderByOccurredAtDesc();
    }

    @GetMapping("/admin/audit/failed-logins")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEvent> failedLogins() {
        return auditEvents.findAllByOrderByOccurredAtDesc().stream()
                .filter(event -> event.getAction().equals("LOGIN_FAILED")).toList();
    }

    @GetMapping("/admin/audit/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportAudit(@RequestParam(defaultValue = "csv") String format) {
        String content = "timestamp,actor,action,entityType,entityId,details\n" + auditEvents
                .findAllByOrderByOccurredAtDesc().stream()
                .map(event -> event.getOccurredAt() + "," + csv(event.getActor()) + ","
                        + csv(event.getAction()) + "," + csv(event.getEntityType()) + ","
                        + csv(event.getEntityId()) + "," + csv(event.getDetails()))
                .collect(Collectors.joining("\n"));
        return export(content, "audit-log", format);
    }

    @PutMapping("/settings/audit-retention")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSetting auditRetention(@Valid @RequestBody SettingRequest request) {
        return setNumericSetting("audit.retention-days", request.value(), BigDecimal.ONE, BigDecimal.valueOf(3650));
    }

    private List<SalesInvoice> invoicesInRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("End date must be on or after start date");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return invoices.findByCreatedAtBetween(start, end.minusNanos(1));
    }

    private BigDecimal revenue(LocalDate from, LocalDate to) {
        return invoicesInRange(from, to).stream()
                .filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED
                        || invoice.getStatus() == SalesInvoice.Status.RETURNED)
                .map(this::netTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netTotal(SalesInvoice invoice) {
        BigDecimal returned = refunds.findByInvoiceId(invoice.getId()).stream()
                .map(RefundRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoice.getTotal().subtract(returned);
    }

    private List<InvoiceLine> soldLines(List<SalesInvoice> rows) {
        return rows.stream().filter(invoice -> invoice.getStatus() == SalesInvoice.Status.COMPLETED
                || invoice.getStatus() == SalesInvoice.Status.RETURNED).flatMap(invoice -> invoice.getLines().stream())
                .toList();
    }

    private Map<String, Object> reconcile(LocalDate date, String till, BigDecimal declared, BigDecimal openingCash) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (SalesInvoice invoice : invoicesInRange(date, date)) {
            if (!invoice.getTillId().equals(till) || invoice.getStatus() == SalesInvoice.Status.VOIDED) {
                continue;
            }
            for (Payment payment : invoice.getPayments()) {
                totals.merge(payment.getMode(), payment.getAmount(), BigDecimal::add);
            }
        }
        for (RefundRecord refund : refunds.findAll()) {
            if (!refund.getProcessedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(date)) {
                continue;
            }
            SalesInvoice invoice = invoices.findById(refund.getInvoiceId()).orElse(null);
            if (invoice != null && invoice.getTillId().equals(till)) {
                totals.merge(refund.getPaymentMode(), refund.getAmount().negate(), BigDecimal::add);
            }
        }
        BigDecimal cashExpenses = expenses.findByOccurredAtBetween(startOf(date), endOf(date)).stream()
                .filter(expense -> expense.getPaymentMode().equalsIgnoreCase("cash")
                        && expense.getTillId().equals(till))
                .map(StoreExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedCash = openingCash.add(totals.getOrDefault("CASH", BigDecimal.ZERO)).subtract(cashExpenses);
        BigDecimal variance = declared.subtract(expectedCash);
        return Map.of("date", date, "tillId", till, "collectionsByMode", totals,
                "openingCash", openingCash, "cashExpenses", cashExpenses,
                "expectedCash", expectedCash, "declaredCash", declared, "variance", variance);
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1);
    }

    private StoreSetting setNumericSetting(String key, String rawValue, BigDecimal minimum, BigDecimal maximum) {
        BigDecimal value;
        try {
            value = new BigDecimal(rawValue);
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest("Setting value must be numeric");
        }
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw ApiException.badRequest("Setting value is outside the allowed range");
        }
        return saveSetting(key, value.toPlainString());
    }

    private StoreSetting saveSetting(String key, String value) {
        if (value == null || value.length() > 2000) {
            throw ApiException.badRequest("Setting value is required and must be at most 2000 characters");
        }
        StoreSetting setting = settings.findById(key).orElseGet(StoreSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting = settings.save(setting);
        audit.record("SETTING_UPDATED", "SETTING", key, value);
        return setting;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private ResponseEntity<byte[]> export(String content, String name, String format) {
        byte[] bytes;
        MediaType type;
        String extension;
        if (format.equalsIgnoreCase("csv")) {
            bytes = content.getBytes(StandardCharsets.UTF_8);
            type = MediaType.parseMediaType("text/csv");
            extension = "csv";
        } else if (format.equalsIgnoreCase("pdf")) {
            bytes = SimplePdf.render(name, content);
            type = MediaType.APPLICATION_PDF;
            extension = "pdf";
        } else {
            throw ApiException.badRequest("format must be csv or pdf");
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "." + extension + "\"")
                .body(bytes);
    }
}
