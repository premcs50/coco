package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.StoreExpense;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreExpenseRepository extends JpaRepository<StoreExpense, Long> {
    List<StoreExpense> findByOccurredAtBetween(Instant start, Instant end);
}
