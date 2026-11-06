package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
