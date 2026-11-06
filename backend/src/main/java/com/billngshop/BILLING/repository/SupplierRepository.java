package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
