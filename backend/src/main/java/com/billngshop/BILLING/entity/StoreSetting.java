package com.billngshop.BILLING.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
public class StoreSetting {
    @Id
    @Column(length = 80)
    private String settingKey;

    @Column(nullable = false, length = 2000)
    private String settingValue;
}
