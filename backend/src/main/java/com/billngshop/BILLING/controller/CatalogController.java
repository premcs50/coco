package com.billngshop.BILLING.controller;

import com.billngshop.BILLING.entity.Category;
import com.billngshop.BILLING.entity.Customer;
import com.billngshop.BILLING.entity.InvoiceLine;
import com.billngshop.BILLING.entity.Notification;
import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.StockMovement;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.entity.Supplier;
import com.billngshop.BILLING.repository.CategoryRepository;
import com.billngshop.BILLING.repository.CustomerRepository;
import com.billngshop.BILLING.repository.NotificationRepository;
import com.billngshop.BILLING.repository.ProductRepository;
import com.billngshop.BILLING.repository.SalesInvoiceRepository;
import com.billngshop.BILLING.repository.StockMovementRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.repository.SupplierRepository;
import com.billngshop.BILLING.repository.PurchaseOrderRepository;
import com.billngshop.BILLING.service.ApiDtos.CategoryRequest;
import com.billngshop.BILLING.service.ApiDtos.CustomerRequest;
import com.billngshop.BILLING.service.ApiDtos.ProductRequest;
import com.billngshop.BILLING.service.ApiDtos.SettingRequest;
import com.billngshop.BILLING.service.ApiDtos.StockAdjustment;
import com.billngshop.BILLING.service.ApiDtos.StockCountRequest;
import com.billngshop.BILLING.service.ApiDtos.SupplierRequest;
import com.billngshop.BILLING.service.ApiException;
import com.billngshop.BILLING.service.AuditService;
import com.billngshop.BILLING.service.InventoryService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
public class CatalogController {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final StoreSettingRepository settings;
    private final NotificationRepository notifications;
    private final SalesInvoiceRepository invoices;
    private final PurchaseOrderRepository purchases;
    private final AuditService audit;
    private final InventoryService inventory;

    public CatalogController(ProductRepository products, CategoryRepository categories,
            CustomerRepository customers, SupplierRepository suppliers, StockMovementRepository movements,
            StoreSettingRepository settings, NotificationRepository notifications,
            SalesInvoiceRepository invoices,
            PurchaseOrderRepository purchases, AuditService audit, InventoryService inventory) {
        this.products = products;
        this.categories = categories;
        this.customers = customers;
        this.suppliers = suppliers;
        this.movements = movements;
        this.settings = settings;
        this.notifications = notifications;
        this.invoices = invoices;
        this.purchases = purchases;
        this.audit = audit;
        this.inventory = inventory;
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        if (products.findBySkuIgnoreCase(request.sku()).isPresent()) {
            throw ApiException.conflict("SKU already exists");
        }
        validateCategory(request.categoryId());
        validateSupplier(request.supplierId());
        Product product = new Product();
        applyProduct(product, request);
        product = products.save(product);
        if (product.getStockQuantity() > 0) {
            inventory.recordMovement(product, product.getStockQuantity(), "INITIAL_STOCK",
                    "Initial stock at product creation", "system");
        }
        audit.record("PRODUCT_CREATED", "PRODUCT", product.getId(), product.getSku());
        return product;
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        Product product = getProduct(id);
        if (!product.getSku().equalsIgnoreCase(request.sku())
                && products.findBySkuIgnoreCase(request.sku()).isPresent()) {
            throw ApiException.conflict("SKU already exists");
        }
        if (request.stockQuantity() != product.getStockQuantity()) {
            throw ApiException.badRequest("Use the stock count or adjustment endpoint to change inventory");
        }
        validateCategory(request.categoryId());
        validateSupplier(request.supplierId());
        boolean priceChanged = product.getSellingPrice().compareTo(request.sellingPrice()) != 0;
        applyProduct(product, request);
        product = products.save(product);
        audit.record(priceChanged ? "PRODUCT_PRICE_CHANGED" : "PRODUCT_UPDATED", "PRODUCT", id, product.getSku());
        return product;
    }

    @GetMapping("/products")
    public List<Product> listProducts(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? products.findAll() : products.findByActiveTrue();
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) {
        return getProduct(id);
    }

    @GetMapping("/products/search")
    public List<Product> searchProducts(@RequestParam String q) {
        return products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q, q);
    }

    @PostMapping("/products/import")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> importProducts(@Valid @RequestBody List<@Valid ProductRequest> rows) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        for (int i = 0; i < rows.size(); i++) {
            ProductRequest row = rows.get(i);
            if (products.findBySkuIgnoreCase(row.sku()).isPresent()) {
                errors.add("Row " + (i + 1) + ": SKU already exists: " + row.sku());
                continue;
            }
            validateCategory(row.categoryId());
            validateSupplier(row.supplierId());
            Product product = new Product();
            applyProduct(product, row);
            product = products.save(product);
            if (product.getStockQuantity() > 0) {
                inventory.recordMovement(product, product.getStockQuantity(), "INITIAL_STOCK",
                        "Bulk product import", "system");
            }
            imported++;
        }
        audit.record("PRODUCT_BULK_IMPORT", "PRODUCT", null, imported + " imported; " + errors.size() + " rejected");
        return Map.of("imported", imported, "errors", errors);
    }

    @PostMapping(value = "/products/import/csv", consumes = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, Object> importProductsCsv(@RequestBody String csv) {
        List<String> rows = csv.lines().filter(line -> !line.isBlank()).toList();
        if (rows.size() < 2) {
            throw ApiException.badRequest("CSV must include a header and at least one product");
        }
        List<String> header = parseCsvRow(rows.get(0));
        Map<String, Integer> columns = new java.util.HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).trim().toLowerCase(), i);
        }
        for (String required : List.of("sku", "name", "sellingprice", "costprice", "stockquantity", "reorderlevel")) {
            if (!columns.containsKey(required)) {
                throw ApiException.badRequest("CSV is missing required column: " + required);
            }
        }
        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            try {
                List<String> values = parseCsvRow(rows.get(rowIndex));
                String sku = csvValue(values, columns, "sku");
                if (products.findBySkuIgnoreCase(sku).isPresent()) {
                    errors.add("Row " + (rowIndex + 1) + ": SKU already exists: " + sku);
                    continue;
                }
                Product product = new Product();
                product.setSku(sku.trim());
                product.setName(csvValue(values, columns, "name").trim());
                product.setSellingPrice(new java.math.BigDecimal(csvValue(values, columns, "sellingprice")));
                product.setCostPrice(new java.math.BigDecimal(csvValue(values, columns, "costprice")));
                product.setStockQuantity(Integer.parseInt(csvValue(values, columns, "stockquantity")));
                product.setReorderLevel(Integer.parseInt(csvValue(values, columns, "reorderlevel")));
                product.setCategoryId(optionalLong(values, columns, "categoryid"));
                product.setSupplierId(optionalLong(values, columns, "supplierid"));
                product.setImageUrl(optionalString(values, columns, "imageurl"));
                if (sku.isBlank() || product.getName().isBlank() || product.getSellingPrice().signum() < 0
                        || product.getCostPrice().signum() < 0 || product.getStockQuantity() < 0
                        || product.getReorderLevel() < 0) {
                    errors.add("Row " + (rowIndex + 1) + ": invalid required values");
                    continue;
                }
                validateCategory(product.getCategoryId());
                validateSupplier(product.getSupplierId());
                product = products.save(product);
                if (product.getStockQuantity() > 0) {
                    inventory.recordMovement(product, product.getStockQuantity(), "INITIAL_STOCK",
                            "CSV product import", "system");
                }
                imported++;
            } catch (ApiException | NumberFormatException exception) {
                errors.add("Row " + (rowIndex + 1) + ": " + exception.getMessage());
            }
        }
        audit.record("PRODUCT_BULK_IMPORT", "PRODUCT", null, imported + " imported; " + errors.size() + " rejected");
        return Map.of("imported", imported, "errors", errors);
    }

    @PutMapping("/products/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Product setProductActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (body == null) {
            throw ApiException.badRequest("active is required");
        }
        Boolean active = body.get("active");
        if (active == null) {
            throw ApiException.badRequest("active is required");
        }
        Product product = getProduct(id);
        product.setActive(active);
        product = products.save(product);
        audit.record(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "PRODUCT", id, product.getSku());
        return product;
    }

    @PutMapping("/products/{id}/image")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Product setProductImage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Product product = getProduct(id);
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw ApiException.badRequest("imageUrl is required");
        }
        if (imageUrl.length() > 2048 || !imageUrl.matches("^https?://[^\\s]+$")) {
            throw ApiException.badRequest("imageUrl must be an http or https URL");
        }
        product.setImageUrl(imageUrl.trim());
        return products.save(product);
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Category createCategory(@Valid @RequestBody CategoryRequest request) {
        if (categories.findByNameIgnoreCase(request.name()).isPresent()) {
            throw ApiException.conflict("Category already exists");
        }
        Category category = new Category();
        category.setName(request.name().trim());
        category = categories.save(category);
        audit.record("CATEGORY_CREATED", "CATEGORY", category.getId(), category.getName());
        return category;
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found"));
        categories.findByNameIgnoreCase(request.name()).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Category already exists");
                });
        category.setName(request.name().trim());
        return categories.save(category);
    }

    @GetMapping("/categories")
    public List<Category> listCategories() {
        return categories.findAll();
    }

    @GetMapping("/inventory/stock")
    public List<Product> stock() {
        return products.findAll();
    }

    @GetMapping("/inventory/low-stock")
    public List<Product> lowStock() {
        return products.findByActiveTrue().stream()
                .filter(product -> product.getStockQuantity() <= product.getReorderLevel()).toList();
    }

    @PostMapping("/inventory/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public Product adjustStock(@Valid @RequestBody StockAdjustment request, Authentication authentication) {
        return inventory.changeStock(request.productId(), request.quantityChange(),
                "ADJUSTMENT", request.reason(), authentication.getName());
    }

    @PostMapping("/inventory/counts")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    @Transactional
    public List<Product> bulkStockCount(@Valid @RequestBody List<@Valid StockCountRequest> counts,
                                        Authentication authentication) {
        List<Product> updated = new ArrayList<>();
        for (StockCountRequest count : counts) {
            Product product = getProduct(count.productId());
            int change = count.countedQuantity() - product.getStockQuantity();
            if (change != 0) {
                inventory.changeStock(product.getId(), change, "PHYSICAL_COUNT",
                        "Physical stock count", authentication.getName());
                product = getProduct(count.productId());
            }
            updated.add(products.save(product));
        }
        audit.record("BULK_STOCK_COUNT", "PRODUCT", null, "Counted " + updated.size() + " products");
        return updated;
    }

    @GetMapping("/inventory/movements")
    public List<StockMovement> stockMovements(@RequestParam(required = false) Long productId) {
        return productId == null ? movements.findAll() : movements.findByProductIdOrderByOccurredAtDesc(productId);
    }

    @GetMapping("/inventory/reorder-suggestions")
    public List<Map<String, Object>> reorderSuggestions() {
        Instant since = Instant.now().minus(java.time.Duration.ofDays(30));
        Map<Long, Integer> recentlySold = invoices.findByCreatedAtBetween(since, Instant.now()).stream()
                .filter(invoice -> invoice.getStatus() == com.billngshop.BILLING.entity.SalesInvoice.Status.COMPLETED)
                .flatMap(invoice -> invoice.getLines().stream())
                .collect(java.util.stream.Collectors.groupingBy(InvoiceLine::getProductId,
                        java.util.stream.Collectors.summingInt(line -> line.getQuantity() - line.getReturnedQuantity())));
        return products.findByActiveTrue().stream()
                .filter(product -> product.getStockQuantity() <= product.getReorderLevel())
                .map(product -> {
                    int recentDemand = recentlySold.getOrDefault(product.getId(), 0);
                    int targetStock = Math.max(product.getReorderLevel() * 2, recentDemand);
                    Supplier preferred = product.getSupplierId() == null ? null
                            : suppliers.findById(product.getSupplierId()).orElse(null);
                    return Map.<String, Object>of(
                            "productId", product.getId(), "sku", product.getSku(), "name", product.getName(),
                            "currentStock", product.getStockQuantity(), "reorderLevel", product.getReorderLevel(),
                            "soldLast30Days", recentDemand,
                            "suggestedQuantity", Math.max(1, targetStock - product.getStockQuantity()),
                            "preferredSupplier", preferred == null ? "" : preferred.getName());
                })
                .toList();
    }

    @PutMapping("/settings/negative-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSetting setNegativeStockPolicy(@Valid @RequestBody SettingRequest request) {
        if (!request.value().equalsIgnoreCase("true") && !request.value().equalsIgnoreCase("false")) {
            throw ApiException.badRequest("value must be true or false");
        }
        return setSetting("inventory.allow-negative-stock", request.value());
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        if (customers.existsByPhoneIgnoreCase(request.phone())) {
            throw ApiException.conflict("A customer with this phone number already exists");
        }
        Customer customer = new Customer();
        applyCustomer(customer, request);
        customer = customers.save(customer);
        audit.record("CUSTOMER_CREATED", "CUSTOMER", customer.getId(), customer.getPhone());
        return customer;
    }

    @GetMapping("/customers")
    public List<Customer> listCustomers(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? customers.findAll() : customers.findAll().stream().filter(Customer::isActive).toList();
    }

    @GetMapping("/customers/search")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public List<Customer> searchCustomers(@RequestParam String q) {
        return customers.findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(q, q).stream()
                .filter(Customer::isActive).toList();
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','BILLING_STAFF')")
    public Customer updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        Customer customer = customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
        boolean duplicatePhone = customers.findByPhoneIgnoreCase(request.phone())
                .filter(existing -> !existing.getId().equals(id)).isPresent();
        if (duplicatePhone) {
            throw ApiException.conflict("A customer with this phone number already exists");
        }
        applyCustomer(customer, request);
        return customers.save(customer);
    }

    @PutMapping("/customers/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public Customer setCustomerActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (body == null) {
            throw ApiException.badRequest("active is required");
        }
        Customer customer = customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
        if (body.get("active") == null) {
            throw ApiException.badRequest("active is required");
        }
        customer.setActive(body.get("active"));
        return customers.save(customer);
    }

    @PostMapping("/customers/{sourceId}/merge/{targetId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Customer mergeCustomers(@PathVariable Long sourceId, @PathVariable Long targetId) {
        if (sourceId.equals(targetId)) {
            throw ApiException.badRequest("Source and target customers must differ");
        }
        Customer source = customers.findById(sourceId).orElseThrow(() -> ApiException.notFound("Source customer not found"));
        Customer target = customers.findById(targetId).orElseThrow(() -> ApiException.notFound("Target customer not found"));
        var historicalInvoices = invoices.findByCustomerId(sourceId);
        historicalInvoices.forEach(invoice -> invoice.setCustomerId(targetId));
        invoices.saveAll(historicalInvoices);
        source.setActive(false);
        customers.save(source);
        audit.record("CUSTOMER_MERGED", "CUSTOMER", sourceId, "Merged into " + targetId);
        return target;
    }

    @GetMapping("/customers/{id}/history")
    public List<Map<String, Object>> customerHistory(@PathVariable Long id) {
        if (!customers.existsById(id)) {
            throw ApiException.notFound("Customer not found");
        }
        return invoices.findByCustomerId(id).stream()
                .map(invoice -> Map.<String, Object>of("invoiceNumber", invoice.getInvoiceNumber(),
                        "createdAt", invoice.getCreatedAt(), "total", invoice.getTotal(), "status", invoice.getStatus()))
                .toList();
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Supplier createSupplier(@Valid @RequestBody SupplierRequest request) {
        Supplier supplier = new Supplier();
        applySupplier(supplier, request);
        supplier = suppliers.save(supplier);
        audit.record("SUPPLIER_CREATED", "SUPPLIER", supplier.getId(), supplier.getName());
        return supplier;
    }

    @GetMapping("/suppliers")
    public List<Supplier> listSuppliers() {
        return suppliers.findAll().stream().filter(Supplier::isActive).toList();
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Supplier updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        Supplier supplier = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found"));
        applySupplier(supplier, request);
        return suppliers.save(supplier);
    }

    @PutMapping("/suppliers/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public Supplier setSupplierActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        if (body == null || body.get("active") == null) {
            throw ApiException.badRequest("active is required");
        }
        Supplier supplier = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found"));
        supplier.setActive(body.get("active"));
        return suppliers.save(supplier);
    }

    @PutMapping("/suppliers/{supplierId}/preferred")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Supplier setPreferredSupplier(@PathVariable Long supplierId, @RequestBody Map<String, Boolean> body) {
        if (body == null || body.get("preferred") == null) {
            throw ApiException.badRequest("preferred is required");
        }
        Supplier supplier = suppliers.findById(supplierId).orElseThrow(() -> ApiException.notFound("Supplier not found"));
        if (body.get("preferred") == null) {
            throw ApiException.badRequest("preferred is required");
        }
        supplier.setPreferred(body.get("preferred"));
        return suppliers.save(supplier);
    }

    @PutMapping("/products/{productId}/supplier")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public Product linkSupplier(@PathVariable Long productId, @RequestBody Map<String, Long> body) {
        if (body == null) {
            throw ApiException.badRequest("supplierId is required");
        }
        Product product = getProduct(productId);
        Long supplierId = body.get("supplierId");
        validateSupplier(supplierId);
        product.setSupplierId(supplierId);
        return products.save(product);
    }

    @GetMapping("/suppliers/{id}/performance")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public Map<String, Object> supplierPerformance(@PathVariable Long id) {
        Supplier supplier = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found"));
        var records = purchases.findBySupplierId(id);
        return Map.of("supplierId", id, "supplierName", supplier.getName(),
                "purchaseCount", records.size(),
                "purchaseVolume", records.stream().map(order -> order.getTotal())
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }

    @GetMapping("/notifications")
    public List<Notification> myNotifications(Authentication authentication) {
        return notifications.findByUsernameOrderByCreatedAtDesc(authentication.getName());
    }

    @PutMapping("/notifications/{id}/read")
    public Notification markNotificationRead(@PathVariable Long id, Authentication authentication) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!notification.getUsername().equals(authentication.getName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update another user's notification");
        }
        notification.setRead(true);
        return notifications.save(notification);
    }

    @PutMapping("/settings/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSetting updateSetting(@PathVariable String key, @Valid @RequestBody SettingRequest request) {
        if (!key.matches("[a-zA-Z0-9._-]{1,80}")) {
            throw ApiException.badRequest("Invalid setting key");
        }
        if (key.startsWith("security.") || key.startsWith("payment.")
                || key.equals("billing.tax-rate-percent") || key.equals("billing.max-discount-percent")
                || key.equals("inventory.allow-negative-stock")) {
            throw ApiException.badRequest("Use the dedicated endpoint for this setting");
        }
        return setSetting(key, request.value());
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StoreSetting> listSettings() {
        return settings.findAll();
    }

    private Product getProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private void applyProduct(Product product, ProductRequest request) {
        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setCategoryId(request.categoryId());
        product.setSupplierId(request.supplierId());
        product.setSellingPrice(request.sellingPrice());
        product.setCostPrice(request.costPrice());
        product.setStockQuantity(request.stockQuantity());
        product.setReorderLevel(request.reorderLevel());
        product.setImageUrl(request.imageUrl());
    }

    private void validateCategory(Long id) {
        if (id != null && !categories.existsById(id)) {
            throw ApiException.badRequest("Category not found");
        }
    }

    private void validateSupplier(Long id) {
        if (id != null && !suppliers.existsById(id)) {
            throw ApiException.badRequest("Supplier not found");
        }
    }

    private StoreSetting setSetting(String key, String value) {
        StoreSetting setting = settings.findById(key).orElseGet(StoreSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting = settings.save(setting);
        audit.record("SETTING_UPDATED", "SETTING", key, value);
        return setting;
    }

    private void applyCustomer(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setPhone(request.phone().trim());
        customer.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
    }

    private void applySupplier(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setContact(request.contact().trim());
        supplier.setEmail(request.email() == null ? null : request.email().trim().toLowerCase());
        supplier.setPreferred(request.preferred());
    }

    private List<String> parseCsvRow(String row) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char current = row.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw ApiException.badRequest("CSV contains an unterminated quoted field");
        }
        values.add(value.toString());
        return values;
    }

    private String csvValue(List<String> values, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= values.size()) {
            throw ApiException.badRequest("Missing CSV value for " + key);
        }
        return values.get(index).trim();
    }

    private Long optionalLong(List<String> values, Map<String, Integer> columns, String key) {
        String value = columns.containsKey(key) ? csvValue(values, columns, key) : "";
        return value.isBlank() ? null : Long.valueOf(value);
    }

    private String optionalString(List<String> values, Map<String, Integer> columns, String key) {
        return columns.containsKey(key) ? csvValue(values, columns, key) : null;
    }
}
