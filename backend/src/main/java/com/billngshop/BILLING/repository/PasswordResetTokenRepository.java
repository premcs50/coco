package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void deleteByUserIdAndUsedAtIsNull(Long userId);
}
