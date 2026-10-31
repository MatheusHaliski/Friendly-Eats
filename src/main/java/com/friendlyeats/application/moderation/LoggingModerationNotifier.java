package com.friendlyeats.application.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Registra os avisos da moderação no log, sem e-mail nem conteúdo (só ids, protocolo e resultado). */
@Component
public class LoggingModerationNotifier implements ModerationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingModerationNotifier.class);

    @Override
    public void decisionToAuthor(long authorId, String protocol, String action, String policy, boolean automated) {
        log.info("Aviso de decisão {} ({}, {}, {}) para a conta {}", protocol, action, policy,
                automated ? "automática" : "humana", authorId);
    }

    @Override
    public void outcomeToReporter(Long reporterId, String email, String protocol, boolean upheld) {
        log.info("Resultado da denúncia {} ({}) para {}", protocol, upheld ? "procedente" : "improcedente",
                reporterId != null ? "a conta " + reporterId : "o e-mail do formulário público");
    }

    @Override
    public void appealResult(long appellantId, String protocol, boolean reversed) {
        log.info("Resultado do recurso do caso {} ({}) para a conta {}", protocol, reversed ? "revertido" : "mantido",
                appellantId);
    }
}
