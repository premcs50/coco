package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.UserAccount;
import com.billngshop.BILLING.repository.UserAccountRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.entity.StoreSetting;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserAccountAuthenticationProvider implements AuthenticationProvider {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final StoreSettingRepository settings;

    public UserAccountAuthenticationProvider(UserAccountRepository users, PasswordEncoder passwordEncoder,
            AuditService audit, StoreSettingRepository settings) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.settings = settings;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String identifier = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        UserAccount account = users.findByUsernameIgnoreCase(identifier)
                .or(() -> users.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> {
                    audit.record("LOGIN_FAILED", "USER", identifier, "Unknown username or email");
                    return new BadCredentialsException("Invalid username or password");
                });
        if (!account.isActive()) {
            audit.record("LOGIN_FAILED", "USER", account.getId(), "Inactive account");
            throw new BadCredentialsException("Invalid username or password");
        }
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(Instant.now())) {
            audit.record("LOGIN_FAILED", "USER", account.getId(), "Account is locked");
            throw new LockedException("Account is temporarily locked");
        }
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            account.setFailedLoginAttempts(account.getFailedLoginAttempts() + 1);
            int threshold = Integer.parseInt(settings.findById("security.login-failure-threshold")
                    .map(StoreSetting::getSettingValue).orElse("5"));
            int lockMinutes = Integer.parseInt(settings.findById("security.login-lock-minutes")
                    .map(StoreSetting::getSettingValue).orElse("15"));
            if (account.getFailedLoginAttempts() >= threshold) {
                account.setLockedUntil(Instant.now().plusSeconds(lockMinutes * 60L));
                account.setFailedLoginAttempts(0);
            }
            users.save(account);
            audit.record("LOGIN_FAILED", "USER", account.getId(), "Invalid credentials");
            throw new BadCredentialsException("Invalid username or password");
        }
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(Instant.now());
        users.save(account);
        return UsernamePasswordAuthenticationToken.authenticated(
                account.getUsername(), null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
