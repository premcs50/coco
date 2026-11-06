package com.billngshop.BILLING.controller;

import com.billngshop.BILLING.entity.PasswordResetToken;
import com.billngshop.BILLING.entity.CashShift;
import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.entity.UserAccount;
import com.billngshop.BILLING.repository.CashShiftRepository;
import com.billngshop.BILLING.repository.PasswordResetTokenRepository;
import com.billngshop.BILLING.repository.UserAccountRepository;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import com.billngshop.BILLING.service.ApiDtos.LoginRequest;
import com.billngshop.BILLING.service.ApiDtos.PasswordResetConfirm;
import com.billngshop.BILLING.service.ApiDtos.RegisterRequest;
import com.billngshop.BILLING.service.ApiException;
import com.billngshop.BILLING.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final AuditService audit;
    private final StoreSettingRepository settings;
    private final CashShiftRepository shifts;
    private final int maximumSessions;
    private final long defaultSessionTimeoutMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthController(
            UserAccountRepository users,
            PasswordResetTokenRepository resetTokens,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry,
            AuditService audit,
            StoreSettingRepository settings,
            CashShiftRepository shifts,
            @Value("${app.security.maximum-concurrent-sessions:1}") int maximumSessions,
            @Value("${app.security.default-session-timeout-minutes:30}") long defaultSessionTimeoutMinutes) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.audit = audit;
        this.settings = settings;
        this.shifts = shifts;
        this.maximumSessions = maximumSessions;
        this.defaultSessionTimeoutMinutes = defaultSessionTimeoutMinutes;
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(@RequestAttribute("_csrf") CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(),
                "token", token.getToken());
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        int sessionLimit = Integer.parseInt(settings.findById("security.maximum-concurrent-sessions")
                .map(StoreSetting::getSettingValue).orElse(Integer.toString(maximumSessions)));
        if (sessionLimit > 0
                && sessionRegistry.getAllSessions(authentication.getName(), false).size() >= sessionLimit) {
            throw new ApiException(HttpStatus.CONFLICT, "Concurrent session limit reached");
        }
        HttpSession session;
        if (servletRequest.getSession(false) == null) {
            session = servletRequest.getSession(true);
        } else {
            servletRequest.changeSessionId();
            session = servletRequest.getSession(false);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse);
        sessionRegistry.registerNewSession(session.getId(), authentication.getName());
        UserAccount account = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        audit.record("LOGIN_SUCCESS", "USER", account.getId(), "Login completed");
        return Map.of("username", account.getUsername(), "role", account.getRole(), "active", account.isActive(),
                "home", roleHome(account.getRole()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       @RequestParam(defaultValue = "false") boolean force) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            CashShift openShift = shifts.findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(authentication.getName())
                    .orElse(null);
            if (openShift != null && !force) {
                throw ApiException.conflict("Close shift " + openShift.getId() + " before logout, or use ?force=true");
            }
            audit.record("LOGOUT", "USER", authentication.getName(), "Manual logout");
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.removeSessionInformation(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        new HttpSessionSecurityContextRepository().saveContext(
                SecurityContextHolder.createEmptyContext(), request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        UserAccount account = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return Map.of("username", account.getUsername(), "email", account.getEmail(),
                "role", account.getRole(), "active", account.isActive(),
                "lastLoginAt", account.getLastLoginAt() == null ? "" : account.getLastLoginAt(),
                "firstName", account.getFirstName() == null ? "" : account.getFirstName(),
                "lastName", account.getLastName() == null ? "" : account.getLastName(),
                "mobileNumber", account.getMobileNumber() == null ? "" : account.getMobileNumber());
    }

    @GetMapping("/auth/session")
    public Map<String, Object> sessionStatus(Authentication authentication, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        CashShift openShift = shifts.findFirstByUsernameAndClosedAtIsNullOrderByOpenedAtDesc(authentication.getName())
                .orElse(null);
        long timeoutMinutes = Long.parseLong(settings.findById("security.session-timeout-minutes")
                .map(StoreSetting::getSettingValue).orElse(Long.toString(defaultSessionTimeoutMinutes)));
        long idleSeconds = session == null ? 0
                : Math.max(0, (System.currentTimeMillis() - session.getLastAccessedTime()) / 1000);
        long remainingSeconds = timeoutMinutes <= 0 ? -1 : Math.max(0, timeoutMinutes * 60 - idleSeconds);
        return Map.of("username", authentication.getName(), "lastActivity",
                session == null ? Instant.now() : Instant.ofEpochMilli(session.getLastAccessedTime()),
                "idleTimeoutSeconds", timeoutMinutes <= 0 ? -1 : timeoutMinutes * 60,
                "remainingSeconds", remainingSeconds,
                "shiftCloseRequired", openShift != null, "openShiftId", openShift == null ? "" : openShift.getId());
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                        Authentication authentication) {
        boolean publicRegistration = authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
        UserAccount.Role assignedRole;
        if (publicRegistration) {
            assignedRole = UserAccount.Role.BILLING_STAFF;
        } else {
            UserAccount.Role operatorRole = users.findByUsernameIgnoreCase(authentication.getName())
                    .orElseThrow(() -> ApiException.notFound("User not found")).getRole();
            if (operatorRole != UserAccount.Role.ADMIN && operatorRole != UserAccount.Role.STORE_MANAGER) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators and store managers may register accounts");
            }
            if (request.role() == null) {
                throw ApiException.badRequest("A role is required for administrator-managed registration");
            }
            if (operatorRole == UserAccount.Role.STORE_MANAGER && request.role() != UserAccount.Role.BILLING_STAFF) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Store managers may register billing staff only");
            }
            assignedRole = request.role();
        }
        if (users.existsByUsernameIgnoreCase(request.username()) || users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("Username or email is already registered");
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.username().trim());
        account.setEmail(request.email().trim().toLowerCase());
        account.setFirstName(cleanOptional(request.firstName()));
        account.setLastName(cleanOptional(request.lastName()));
        account.setMobileNumber(cleanOptional(request.mobileNumber()));
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(assignedRole);
        account.setActive(false);
        account = users.save(account);
        audit.record("USER_REGISTERED", "USER", account.getId(), "Role " + account.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(userSummary(account));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<Map<String, Object>> listUsers() {
        return users.findAll().stream().map(this::userSummary).toList();
    }

    @PutMapping("/users/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            throw ApiException.badRequest("active is required");
        }
        UserAccount account = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        account.setActive(active);
        account = users.save(account);
        if (!active) {
            sessionRegistry.getAllSessions(account.getUsername(), false).forEach(session -> session.expireNow());
        }
        audit.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", id, null);
        return userSummary(account);
    }

    @GetMapping("/admin/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<Map<String, Object>> activeSessions() {
        return sessionRegistry.getAllPrincipals().stream().flatMap(principal ->
                sessionRegistry.getAllSessions(principal, false).stream().map(session ->
                        Map.<String, Object>of("username", principal.toString(),
                                "sessionId", session.getSessionId(),
                                "lastRequest", session.getLastRequest()))).toList();
    }

    @PostMapping("/admin/users/{id}/force-logout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> forceLogout(@PathVariable Long id) {
        UserAccount account = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        sessionRegistry.getAllSessions(account.getUsername(), false).forEach(session -> {
            session.expireNow();
        });
        audit.record("FORCE_LOGOUT", "USER", id, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/users/{id}/password-reset-token")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, String> issueResetToken(@PathVariable Long id) {
        UserAccount account = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        resetTokens.deleteByUserIdAndUsedAtIsNull(account.getId());
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setUserId(account.getId());
        resetTokens.save(token);
        audit.record("PASSWORD_RESET_TOKEN_ISSUED", "USER", id, "Expires in 30 minutes");
        return Map.of("token", rawToken, "expiresInSeconds", "1800");
    }

    @PostMapping("/auth/password-reset")
    @Transactional
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirm request) {
        PasswordResetToken token = resetTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> ApiException.badRequest("Reset token is invalid or expired"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token is invalid or expired");
        }
        UserAccount account = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        token.setUsedAt(Instant.now());
        users.save(account);
        resetTokens.save(token);
        sessionRegistry.getAllSessions(account.getUsername(), false).forEach(session -> session.expireNow());
        audit.record("PASSWORD_RESET", "USER", account.getId(), null);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> userSummary(UserAccount account) {
        return Map.of("id", account.getId(), "username", account.getUsername(), "email", account.getEmail(),
                "role", account.getRole(), "active", account.isActive(), "createdAt", account.getCreatedAt(),
                "firstName", account.getFirstName() == null ? "" : account.getFirstName(),
                "lastName", account.getLastName() == null ? "" : account.getLastName(),
                "mobileNumber", account.getMobileNumber() == null ? "" : account.getMobileNumber());
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String roleHome(UserAccount.Role role) {
        return switch (role) {
            case ADMIN, STORE_MANAGER -> "/api/dashboard";
            case INVENTORY_STAFF -> "/api/inventory/stock";
            case BILLING_STAFF -> "/api/invoices";
        };
    }
}
