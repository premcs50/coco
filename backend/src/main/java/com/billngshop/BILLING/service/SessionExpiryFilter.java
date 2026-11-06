package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.StoreSetting;
import com.billngshop.BILLING.repository.StoreSettingRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionExpiryFilter extends OncePerRequestFilter {
    private final SessionRegistry sessions;
    private final StoreSettingRepository settings;
    private final long defaultTimeoutMinutes;

    public SessionExpiryFilter(SessionRegistry sessions, StoreSettingRepository settings, long defaultTimeoutMinutes) {
        this.sessions = sessions;
        this.settings = settings;
        this.defaultTimeoutMinutes = defaultTimeoutMinutes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) {
            var information = sessions.getSessionInformation(session.getId());
            long timeoutMinutes = settings.findById("security.session-timeout-minutes")
                    .map(StoreSetting::getSettingValue).map(Long::parseLong).orElse(defaultTimeoutMinutes);
            session.setMaxInactiveInterval(timeoutMinutes <= 0 ? -1 : Math.toIntExact(timeoutMinutes * 60));
            boolean idleExpired = timeoutMinutes > 0
                    && System.currentTimeMillis() - session.getLastAccessedTime() > timeoutMinutes * 60_000L;
            if (idleExpired || information != null && information.isExpired()) {
                sessions.removeSessionInformation(session.getId());
                session.invalidate();
                SecurityContextHolder.clearContext();
                new HttpSessionSecurityContextRepository().saveContext(
                        SecurityContextHolder.createEmptyContext(), request, response);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"status\":401,\"message\":\"Session expired; please log in again\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
