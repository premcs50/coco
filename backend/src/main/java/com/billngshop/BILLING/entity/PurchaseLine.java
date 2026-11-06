package com.billngshop.BILLING.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_lines")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    @JsonIgnore
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int orderedQuantity;

    @Column(nullable = false)
    private int receivedQuantity;

    @Column(nullable = false)
    private int returnedQuantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;
}
