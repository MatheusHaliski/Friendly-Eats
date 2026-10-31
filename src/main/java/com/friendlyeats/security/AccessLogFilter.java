package com.friendlyeats.security;

import com.friendlyeats.application.privacy.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registro de acesso do Marco Civil (RF18) para quem já está logado: data, hora, IP e porta de origem, no máximo um
 * por usuário e IP a cada hora ({@link AccessLogService#access}). O login grava à parte, em
 * {@link SessionService#establish}. Criado no {@link SecurityConfig}, dentro da cadeia do Spring Security (não é bean,
 * para não rodar duas vezes).
 *
 * <p>⚖️/infra: atrás de proxy ou balanceador, ligue {@code server.forward-headers-strategy} para que
 * {@code getRemoteAddr()} e {@code getRemotePort()} sejam os do cliente, e não os do proxy.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogService accessLog;

    public AccessLogFilter(AccessLogService accessLog) {
        this.accessLog = accessLog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof FriendlyPrincipal p) {
            accessLog.access(p.userId(), request.getRemoteAddr(), request.getRemotePort(), channel(request));
        }
        chain.doFilter(request, response);
    }

    static String channel(HttpServletRequest request) {
        String c = request.getHeader(ClientInfo.CHANNEL_HEADER);
        return c != null && c.matches("(?i)WEB|IOS|ANDROID") ? c.toUpperCase(java.util.Locale.ROOT) : "WEB";
    }
}
