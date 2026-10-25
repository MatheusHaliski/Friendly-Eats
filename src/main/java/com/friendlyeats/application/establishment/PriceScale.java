package com.friendlyeats.application.establishment;

import com.friendlyeats.domain.taxonomy.PriceLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Escala nacional de preço por pessoa (RF6; docs/TAXONOMIA_FRIENDLY_EATS.md). Os limites são múltiplos de uma
 * refeição de referência R (R$ 58 em 2026: ABBT + IPCA de alimentação fora do domicílio), dobrando a cada nível e
 * arredondados para R$ 5: até R$ 40, R$ 40–80, R$ 80–160 e acima de R$ 160. Intervalos semiabertos [início, fim):
 * R$ 40,00 já é {@code PRICE_2}. O reajuste anual muda a configuração; os valores em reais guardados não mudam.
 */
@Component
public class PriceScale {

    private final int[] thresholdsCents;

    public PriceScale(@Value("${friendly-eats.price.reference-brl:58}") double referenceBrl,
                      @Value("${friendly-eats.price.multipliers:0.7,1.4,2.8}") double[] multipliers,
                      @Value("${friendly-eats.price.round-brl:5}") int roundBrl) {
        if (multipliers.length != 3) {
            throw new IllegalArgumentException("A escala de preço precisa de 3 multiplicadores");
        }
        this.thresholdsCents = java.util.Arrays.stream(multipliers)
                .mapToInt(m -> (int) Math.round(referenceBrl * m / roundBrl) * roundBrl * 100)
                .toArray();
    }

    public PriceLevel levelOf(int centsPerPerson) {
        if (centsPerPerson < thresholdsCents[0]) return PriceLevel.PRICE_1;
        if (centsPerPerson < thresholdsCents[1]) return PriceLevel.PRICE_2;
        if (centsPerPerson < thresholdsCents[2]) return PriceLevel.PRICE_3;
        return PriceLevel.PRICE_4;
    }

    /** "até R$ 40", "R$ 40 a R$ 80"… ({@code null} para desconhecido e não se aplica). */
    public String rangeLabel(PriceLevel level) {
        return switch (level) {
            case PRICE_1 -> "até " + brl(thresholdsCents[0]);
            case PRICE_2 -> brl(thresholdsCents[0]) + " a " + brl(thresholdsCents[1]);
            case PRICE_3 -> brl(thresholdsCents[1]) + " a " + brl(thresholdsCents[2]);
            case PRICE_4 -> "acima de " + brl(thresholdsCents[2]);
            default -> null;
        };
    }

    public int[] thresholdsCents() {
        return thresholdsCents.clone();
    }

    static String brl(int cents) {
        return cents % 100 == 0 ? "R$ " + cents / 100 : String.format("R$ %d,%02d", cents / 100, cents % 100);
    }
}
