package com.friendlyeats.application.guardian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Todo dia avisa o menor cujo convite ao responsável segue pendente (RF17). Decisão: a conta não é bloqueada; ela
 * já fica com as proteções máximas enquanto o vínculo não é aceito.
 */
@Component
@ConditionalOnProperty(name = "friendly-eats.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class GuardianReminderJob {

    private static final Logger log = LoggerFactory.getLogger(GuardianReminderJob.class);

    private final GuardianService guardians;

    public GuardianReminderJob(GuardianService guardians) {
        this.guardians = guardians;
    }

    @Scheduled(cron = "${friendly-eats.guardian.reminder-cron:0 40 10 * * *}", zone = "America/Sao_Paulo")
    public int run() {
        int n = guardians.remindPending();
        if (n > 0) {
            log.info("Vínculo com responsável pendente: {} aviso(s) enviado(s)", n);
        }
        return n;
    }
}
