package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.PurchaseOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findBySupplierId(Long supplierId);
}
