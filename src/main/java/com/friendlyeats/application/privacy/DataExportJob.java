package com.friendlyeats.application.privacy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gera as exportações pedidas (RF18) e expira os links vencidos; roda a cada {@code export-poll}. */
@Component
@ConditionalOnProperty(name = "friendly-eats.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DataExportJob {

    private final DataExportService exports;

    public DataExportJob(DataExportService exports) {
        this.exports = exports;
    }

    @Scheduled(fixedDelayString = "${friendly-eats.privacy.export-poll:30s}", initialDelayString = "PT20S")
    public void run() {
        exports.processPending();
        exports.expire();
    }
}
