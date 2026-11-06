package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.StockMovement;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByOccurredAtDesc(Long productId);
    List<StockMovement> findByOccurredAtBetween(Instant start, Instant end);
}
