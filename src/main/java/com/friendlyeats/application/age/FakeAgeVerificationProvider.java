package com.friendlyeats.application.age;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Fornecedor simulado, só nos perfis {@code dev} e {@code it}: o resultado vem do parâmetro {@code simulate}
 * ({@code ADULT}, {@code MINOR} ou {@code FAILED}). Nunca sobe em produção, onde a verificação exige um fornecedor
 * contratado (⚖️, ver {@link AgeVerificationProvider}).
 */
@Component
@Profile({"dev", "it"})
public class FakeAgeVerificationProvider implements AgeVerificationProvider {

    static final Duration TTL = Duration.ofMinutes(30);

    private final Clock clock;

    public FakeAgeVerificationProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String id() {
        return "fake-dev";
    }

    @Override
    public Session start(long userId, String returnUrl) {
        String reference = "fake-" + UUID.randomUUID();
        return new Session(reference, returnUrl + (returnUrl.contains("?") ? "&" : "?") + "verificacao=" + reference,
                "Ambiente de desenvolvimento: escolha o resultado da verificação simulada.",
                clock.instant().plus(TTL));
    }

    @Override
    public Result result(long userId, String reference, Map<String, String> params) {
        String simulate = params == null ? null : params.get("simulate");
        if (simulate == null) {
            return new Result(Outcome.PENDING, null);
        }
        return switch (simulate) {
            case "ADULT" -> new Result(Outcome.ADULT, "SIMULADO");
            case "MINOR" -> new Result(Outcome.MINOR, "SIMULADO");
            default -> new Result(Outcome.FAILED, "SIMULADO");
        };
    }

    @Override
    public boolean simulated() {
        return true;
    }
}
