package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    List<InvoiceLine> findByProductId(Long productId);
}
