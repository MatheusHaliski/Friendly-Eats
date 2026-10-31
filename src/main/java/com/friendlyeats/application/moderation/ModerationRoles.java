package com.friendlyeats.application.moderation;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Papel de moderador (RF13). A sessão já leva a autoridade {@code ROLE_MODERATOR} (o {@code SecurityConfig} fecha
 * {@code /api/v1/moderation/**} para os outros), e o serviço confere de novo no banco a cada ação: tirar o papel vale
 * na hora, sem esperar a sessão acabar. O papel é concedido pela lista {@code friendly-eats.moderation.bootstrap-moderators}
 * (e-mails, na subida da aplicação) e pelo DevSeed no perfil dev.
 */
@Component
@Order(100)
public class ModerationRoles implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModerationRoles.class);

    private final UserRoleRepository roles;
    private final UserAccountRepository users;
    private final List<String> bootstrap;

    public ModerationRoles(UserRoleRepository roles, UserAccountRepository users,
                           @Value("${friendly-eats.moderation.bootstrap-moderators:}") String bootstrap) {
        this.roles = roles;
        this.users = users;
        this.bootstrap = Arrays.stream(bootstrap.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isModerator(long userId) {
        return roles.has(userId, UserRoleRepository.MODERATOR);
    }

    /** {@code 403} se a conta perdeu o papel depois de entrar. */
    public void requireModerator(long userId) {
        if (!isModerator(userId)) {
            throw new CommonExceptions.Forbidden("Só a equipe de moderação acessa esta área.");
        }
    }

    public boolean grant(long userId, String grantedBy) {
        return roles.grant(userId, UserRoleRepository.MODERATOR, grantedBy);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : bootstrap) {
            users.findByEmail(email.toLowerCase(java.util.Locale.ROOT)).ifPresentOrElse(u -> {
                if (grant(u.getId(), "config")) {
                    log.info("Papel MODERATOR concedido por configuração à conta {}", u.getId());
                }
            }, () -> log.warn("bootstrap-moderators: nenhuma conta com o e-mail configurado (item {} da lista)",
                    bootstrap.indexOf(email) + 1));
        }
    }
}
