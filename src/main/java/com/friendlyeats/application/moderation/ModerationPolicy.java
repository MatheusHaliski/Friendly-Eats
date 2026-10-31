package com.friendlyeats.application.moderation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Números da moderação (RF13), todos em {@code friendly-eats.moderation.*} e {@code friendly-eats.integrity.*}.
 * Os prazos e limites são metas da casa ⚖️, a confirmar pelo dono do produto e pelo jurídico.
 */
@Component
public class ModerationPolicy {

    private final int reportsPerHour;
    private final int reportsPerDay;
    private final int publicReportsPerHour;
    private final Duration appealWindow;
    private final int appealResponseDays;
    private final int ownerContestsPerMonth;
    private final int lowTrustMinDismissed;
    private final double lowTrustRatio;
    private final Duration lowTrustWindow;
    private final int newAccountDays;
    private final Duration burstWindow;
    private final int burstThreshold;
    private final int freezeThreshold;
    private final int freezeDays;
    private final Duration sameOriginWindow;
    private volatile boolean signalsEnabled;

    public ModerationPolicy(@Value("${friendly-eats.moderation.reports-per-hour:10}") int reportsPerHour,
                            @Value("${friendly-eats.moderation.reports-per-day:50}") int reportsPerDay,
                            @Value("${friendly-eats.moderation.public-reports-per-hour:5}") int publicReportsPerHour,
                            @Value("${friendly-eats.moderation.appeal-days:30}") int appealDays,
                            @Value("${friendly-eats.moderation.appeal-response-days:7}") int appealResponseDays,
                            @Value("${friendly-eats.moderation.owner-contests-per-month:10}") int ownerContestsPerMonth,
                            @Value("${friendly-eats.moderation.low-trust.min-dismissed:3}") int lowTrustMinDismissed,
                            @Value("${friendly-eats.moderation.low-trust.ratio:0.7}") double lowTrustRatio,
                            @Value("${friendly-eats.moderation.low-trust.window-days:90}") int lowTrustWindowDays,
                            @Value("${friendly-eats.integrity.new-account-days:7}") int newAccountDays,
                            @Value("${friendly-eats.integrity.burst-window-hours:24}") int burstWindowHours,
                            @Value("${friendly-eats.integrity.burst-threshold:3}") int burstThreshold,
                            @Value("${friendly-eats.integrity.freeze-threshold:5}") int freezeThreshold,
                            @Value("${friendly-eats.integrity.freeze-days:7}") int freezeDays,
                            @Value("${friendly-eats.integrity.same-origin-days:30}") int sameOriginDays,
                            @Value("${friendly-eats.integrity.signals-enabled:true}") boolean signalsEnabled) {
        this.reportsPerHour = reportsPerHour;
        this.reportsPerDay = reportsPerDay;
        this.publicReportsPerHour = publicReportsPerHour;
        this.appealWindow = Duration.ofDays(appealDays);
        this.appealResponseDays = appealResponseDays;
        this.ownerContestsPerMonth = ownerContestsPerMonth;
        this.lowTrustMinDismissed = lowTrustMinDismissed;
        this.lowTrustRatio = lowTrustRatio;
        this.lowTrustWindow = Duration.ofDays(lowTrustWindowDays);
        this.newAccountDays = newAccountDays;
        this.burstWindow = Duration.ofHours(burstWindowHours);
        this.burstThreshold = burstThreshold;
        this.freezeThreshold = freezeThreshold;
        this.freezeDays = freezeDays;
        this.sameOriginWindow = Duration.ofDays(sameOriginDays);
        this.signalsEnabled = signalsEnabled;
    }

    /** Sinais automáticos de rajada e de mesma origem ligados (o vínculo declarado é sempre registrado). */
    public boolean signalsEnabled() {
        return signalsEnabled;
    }

    /**
     * Liga ou desliga os sinais em tempo de execução: os testes de integração saem todos de 127.0.0.1 com contas
     * recém-criadas (desligado no perfil {@code it}, ligado só no teste da integridade), e a operação pode desligar
     * num falso positivo em massa.
     */
    public void setSignalsEnabled(boolean enabled) {
        this.signalsEnabled = enabled;
    }

    public int reportsPerHour() { return reportsPerHour; }
    public int reportsPerDay() { return reportsPerDay; }
    public int publicReportsPerHour() { return publicReportsPerHour; }
    public Duration appealWindow() { return appealWindow; }
    public int appealResponseDays() { return appealResponseDays; }
    public int ownerContestsPerMonth() { return ownerContestsPerMonth; }
    public Duration lowTrustWindow() { return lowTrustWindow; }
    public int newAccountDays() { return newAccountDays; }
    public Duration burstWindow() { return burstWindow; }
    public int burstThreshold() { return burstThreshold; }
    public int freezeThreshold() { return freezeThreshold; }
    public int freezeDays() { return freezeDays; }
    public Duration sameOriginWindow() { return sameOriginWindow; }

    /**
     * Denunciante pouco confiável: {@code min-dismissed} ou mais denúncias improcedentes na janela e a proporção de
     * improcedentes acima de {@code ratio}. A denúncia dele continua valendo, só desce um nível na fila (sem punição
     * nem aviso: arts. 32 e 33 do ECA Digital pedem sanção só por abuso comprovado, com recurso).
     */
    public boolean lowTrust(long dismissed, long resolved) {
        return dismissed >= lowTrustMinDismissed && resolved > 0 && (double) dismissed / resolved >= lowTrustRatio;
    }
}
