package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.Notification;
import com.billngshop.BILLING.entity.Product;
import com.billngshop.BILLING.entity.StockMovement;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.entity.UserAccount;
import com.billngshop.BILLING.repository.NotificationRepository;
import com.billngshop.BILLING.repository.ProductRepository;
import com.billngshop.BILLING.repository.StockMovementRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.repository.UserAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final StoreSettingRepository settings;
    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final AuditService audit;

    public InventoryService(ProductRepository products, StockMovementRepository movements,
            StoreSettingRepository settings, NotificationRepository notifications,
            UserAccountRepository users, AuditService audit) {
        this.products = products;
        this.movements = movements;
        this.settings = settings;
        this.notifications = notifications;
        this.users = users;
        this.audit = audit;
    }

    public Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    @Transactional
    public Product changeStock(Long productId, int change, String type, String reason, String actor) {
        Product product = requireProduct(productId);
        int previousQuantity = product.getStockQuantity();
        int updated = product.getStockQuantity() + change;
        boolean allowNegative = Boolean.parseBoolean(settings.findById("inventory.allow-negative-stock")
                .map(StoreSetting::getSettingValue).orElse("false"));
        if (updated < 0 && !allowNegative) {
            throw ApiException.conflict("Insufficient stock for product " + product.getSku());
        }
        product.setStockQuantity(updated);
        product = products.save(product);
        recordMovement(product, change, type, reason, actor,
                previousQuantity > product.getReorderLevel() && updated <= product.getReorderLevel());
        audit.record("STOCK_" + type, "PRODUCT", product.getId(), change + ": " + reason);
        return product;
    }

    @Transactional
    public void recordMovement(Product product, int change, String type, String reason, String actor) {
        recordMovement(product, change, type, reason, actor,
                product.getStockQuantity() <= product.getReorderLevel());
    }

    private void recordMovement(Product product, int change, String type, String reason, String actor,
                                boolean shouldNotify) {
        StockMovement movement = new StockMovement();
        movement.setProductId(product.getId());
        movement.setQuantityChange(change);
        movement.setMovementType(type);
        movement.setReason(reason);
        movement.setActor(actor);
        movements.save(movement);
        if (shouldNotify) {
            List<UserAccount> recipients = users.findAll().stream()
                    .filter(UserAccount::isActive)
                    .filter(user -> user.getRole() == UserAccount.Role.ADMIN
                            || user.getRole() == UserAccount.Role.STORE_MANAGER
                            || user.getRole() == UserAccount.Role.INVENTORY_STAFF)
                    .toList();
            for (UserAccount user : recipients) {
                boolean enabled = Boolean.parseBoolean(settings.findById(
                                "notifications." + user.getUsername() + ".lowStock.enabled")
                        .map(StoreSetting::getSettingValue).orElse("true"));
                if (!enabled) {
                    continue;
                }
                Notification notification = new Notification();
                notification.setUsername(user.getUsername());
                notification.setTitle("Low stock: " + product.getName());
                notification.setMessage("Current stock is " + product.getStockQuantity()
                        + "; reorder level is " + product.getReorderLevel());
                notifications.save(notification);
            }
        }
    }
}
