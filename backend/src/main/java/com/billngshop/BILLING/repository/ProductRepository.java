package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySkuIgnoreCase(String sku);
    List<Product> findByActiveTrue();
    List<Product> findByStockQuantityLessThanEqualAndActiveTrue(int quantity);
    List<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku);
}
