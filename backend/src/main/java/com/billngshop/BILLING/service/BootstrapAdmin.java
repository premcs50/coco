package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.UserAccount;
import com.billngshop.BILLING.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdmin(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && email.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Set all APP_BOOTSTRAP_ADMIN_USERNAME, EMAIL and PASSWORD values");
        }
        if (users.count() > 0) {
            return;
        }
        if (password.length() < 10 || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$")) {
            throw new IllegalStateException("Bootstrap admin password must meet the configured password policy");
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserAccount.Role.ADMIN);
        users.save(admin);
    }
}
