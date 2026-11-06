package com.billngshop.BILLING.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cash_shifts")
@Getter
@Setter
@NoArgsConstructor
public class CashShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 40)
    private String tillId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    private BigDecimal closingCash;
    private Instant closedAt;

    @Column(nullable = false)
    private Instant openedAt;

    @PrePersist
    void onCreate() {
        openedAt = Instant.now();
    }
}
