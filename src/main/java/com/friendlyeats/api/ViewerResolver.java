package com.friendlyeats.api;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.security.FriendlyPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Quem está vendo/agindo, a partir da sessão autenticada (cookie de sessão opaco · RF4).
 * Sem sessão — inclusive numa aba anônima, que não compartilha cookies — o pedido é anônimo:
 * leitura pública funciona e escrita responde 401.
 */
@Component
public class ViewerResolver {

    public Optional<Long> viewerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof FriendlyPrincipal p
                ? Optional.of(p.userId())
                : Optional.empty();
    }

    public long requireViewer() {
        return viewerId().orElseThrow(CommonExceptions.Unauthenticated::new);
    }
}
