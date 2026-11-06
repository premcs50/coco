package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.RefundRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {
    List<RefundRecord> findByInvoiceId(Long invoiceId);
}
