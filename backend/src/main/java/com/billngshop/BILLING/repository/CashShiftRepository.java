package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.CashShift;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {
    List<CashShift> findByUsernameOrderByOpenedAtDesc(String username);
    Optional<CashShift> findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(String username);
}
