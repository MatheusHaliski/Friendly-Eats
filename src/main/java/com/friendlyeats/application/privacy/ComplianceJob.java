package com.friendlyeats.application.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina diária de conformidade (RF18): apaga os registros de acesso com mais de 6 meses (Marco Civil, art. 15),
 * apaga os incidentes com mais de 5 anos (Resolução CD/ANPD 15/2024) e avisa no log os incidentes com o prazo de
 * comunicação de 3 dias úteis vencido sem decisão.
 */
@Component
@ConditionalOnProperty(name = "friendly-eats.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ComplianceJob {

    private static final Logger log = LoggerFactory.getLogger(ComplianceJob.class);

    private final AccessLogService accessLog;
    private final SecurityIncidentService incidents;

    public ComplianceJob(AccessLogService accessLog, SecurityIncidentService incidents) {
        this.accessLog = accessLog;
        this.incidents = incidents;
    }

    @Scheduled(cron = "${friendly-eats.privacy.compliance-cron:0 25 4 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        int logs = accessLog.purgeExpired();
        int old = incidents.purgeExpired();
        incidents.overdue().forEach(i -> log.error("Incidente {} ({}) com prazo de comunicação vencido em {} sem decisão",
                i.id(), i.title(), i.communicationDeadline()));
        if (logs > 0 || old > 0) {
            log.info("Conformidade: {} registro(s) de acesso e {} incidente(s) além do prazo de guarda apagados", logs, old);
        }
    }
}
