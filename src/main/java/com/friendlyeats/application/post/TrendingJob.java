package com.friendlyeats.application.post;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recalcula o ranking "em alta" (RF14) a cada 10 minutos, aplicando o decaimento por idade. */
@Component
@ConditionalOnProperty(name = "friendly-eats.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class TrendingJob {

    private static final Logger log = LoggerFactory.getLogger(TrendingJob.class);

    private final TrendingService trending;

    public TrendingJob(TrendingService trending) {
        this.trending = trending;
    }

    @Scheduled(fixedDelayString = "${friendly-eats.trending.recalculate-every:PT10M}",
            initialDelayString = "${friendly-eats.trending.initial-delay:PT1M}")
    public void run() {
        try {
            trending.recalculate();
        } catch (RuntimeException e) {
            log.warn("Recálculo do ranking em alta falhou; nova tentativa no próximo ciclo: {}", e.getMessage());
        }
    }
}
