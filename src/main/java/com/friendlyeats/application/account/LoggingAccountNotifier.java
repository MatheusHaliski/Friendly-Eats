package com.friendlyeats.application.account;

import com.friendlyeats.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Registra os avisos no log com e-mail mascarado. O código de verificação só aparece no log com
 * {@code friendly-eats.notifications.log-codes=true} (perfil dev), nunca em produção. O token do convite do
 * responsável (RF17) nunca aparece.
 */
@Component
public class LoggingAccountNotifier implements AccountNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAccountNotifier.class);

    private final boolean logCodes;

    public LoggingAccountNotifier(@Value("${friendly-eats.notifications.log-codes:false}") boolean logCodes) {
        this.logCodes = logCodes;
    }

    @Override
    public void verificationCode(UserAccount user, String code) {
        log.info("Código de verificação enviado para {}{}", mask(user.getEmail()), logCodes ? " (dev: " + code + ")" : "");
    }

    @Override
    public void loginAlert(UserAccount user, String device) {
        log.info("Alerta de login para {}: {}", mask(user.getEmail()), device);
    }

    @Override
    public void passwordChanged(UserAccount user) {
        log.info("Aviso de senha alterada para {}", mask(user.getEmail()));
    }

    @Override
    public void mfaChanged(UserAccount user, boolean enabled) {
        log.info("Aviso de 2FA {} para {}", enabled ? "ativado" : "desativado", mask(user.getEmail()));
    }

    @Override
    public void deletionScheduled(UserAccount user, Instant deletionAt) {
        log.info("Aviso de exclusão agendada ({}) para {}", deletionAt, mask(user.getEmail()));
    }

    @Override
    public void accountReactivated(UserAccount user) {
        log.info("Aviso de conta reativada para {}", mask(user.getEmail()));
    }

    @Override
    public void guardianInvite(String guardianEmail, UserAccount minor, String token, Instant expiresAt) {
        // o token é uma credencial (quem tem o link vira responsável): nunca vai para o log, nem em dev
        log.info("Convite de responsável enviado para {} (menor {}, vale até {})", mask(guardianEmail), minor.getId(),
                expiresAt);
    }

    @Override
    public void guardianLinked(UserAccount minor, UserAccount guardian) {
        log.info("Aviso de supervisão ativa para {}", mask(minor.getEmail()));
    }

    @Override
    public void guardianLinkPending(UserAccount minor, long days) {
        log.info("Aviso de vínculo com o responsável pendente há {} dias para {}", days, mask(minor.getEmail()));
    }

    @Override
    public void guardianRequest(UserAccount guardian, UserAccount minor, String protectionLabel) {
        log.info("Pedido do menor {} ({}) avisado ao responsável {}", minor.getId(), protectionLabel,
                mask(guardian.getEmail()));
    }

    @Override
    public void ageAppealDecided(UserAccount user, boolean accepted) {
        log.info("Contestação de idade {} avisada a {}", accepted ? "aceita" : "recusada", mask(user.getEmail()));
    }

    @Override
    public void dataExportRequested(UserAccount user, Instant dueAt) {
        log.info("Aviso de pedido de exportação (prazo {}) para {}", dueAt, mask(user.getEmail()));
    }

    @Override
    public void dataExportReady(UserAccount user, Instant expiresAt) {
        log.info("Aviso de exportação pronta (até {}) para {}", expiresAt, mask(user.getEmail()));
    }

    static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] p = email.split("@", 2);
        return p[0].charAt(0) + "***@" + p[1].charAt(0) + "***";
    }
}
