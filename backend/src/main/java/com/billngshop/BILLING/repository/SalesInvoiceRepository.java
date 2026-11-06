package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.SalesInvoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {
    Optional<SalesInvoice> findByInvoiceNumber(String invoiceNumber);
    List<SalesInvoice> findByCreatedAtBetween(Instant start, Instant end);
    List<SalesInvoice> findByCustomerId(Long customerId);
}
