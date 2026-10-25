package com.friendlyeats.application.establishment;

import com.friendlyeats.domain.taxonomy.ReviewDimension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nota agregada (RF8). Duas contas separadas:
 * <ul>
 *   <li><b>nota exibida</b>: média das notas ponderada por confiança × recência (meia-vida de 365 dias, piso de 25%),
 *       só a partir de 5 avaliações — explicável ao consumidor (CDC art. 6º, III);</li>
 *   <li><b>score de ranking</b>: média bayesiana {@code (m·C + Σwᵢrᵢ) / (m + Σwᵢ)} com m = 10, para que um 5,0 com
 *       2 avaliações não passe à frente de um 4,6 com 400.</li>
 * </ul>
 * Sem avaliação é {@code RATING_NONE} (nunca 0); de 1 a 4 é {@code RATING_FEW}, sem média.
 */
@Component
public class RatingCalculator {

    public enum State { RATING_NONE, RATING_FEW, RATED }

    /** Uma avaliação publicada, já com o peso de confiança da verificação. */
    public record Sample(int rating, double trustWeight, Instant experiencedAt, Map<ReviewDimension, Integer> dimensions) {
    }

    public record Summary(State state, BigDecimal average, String averageLabel, int count,
                          Map<Integer, Integer> histogram, Map<ReviewDimension, BigDecimal> dimensions,
                          String accessibleLabel, double rankingScore) {
    }

    private final double priorMean;
    private final double priorWeight;
    private final int minDisplay;
    private final double halfLifeDays;
    private final double recencyFloor;
    private final Clock clock;

    public RatingCalculator(@Value("${friendly-eats.rating.prior-mean:4.2}") double priorMean,
                            @Value("${friendly-eats.rating.prior-weight:10}") double priorWeight,
                            @Value("${friendly-eats.rating.min-display:5}") int minDisplay,
                            @Value("${friendly-eats.rating.half-life-days:365}") double halfLifeDays,
                            @Value("${friendly-eats.rating.recency-floor:0.25}") double recencyFloor, Clock clock) {
        this.priorMean = priorMean;
        this.priorWeight = priorWeight;
        this.minDisplay = minDisplay;
        this.halfLifeDays = halfLifeDays;
        this.recencyFloor = recencyFloor;
        this.clock = clock;
    }

    public Summary summarize(List<Sample> samples) {
        int n = samples.size();
        double sumW = 0;
        double sumWR = 0;
        for (Sample s : samples) {
            double w = weight(s);
            sumW += w;
            sumWR += w * s.rating();
        }
        double ranking = (priorWeight * priorMean + sumWR) / (priorWeight + sumW);
        if (n == 0) {
            return new Summary(State.RATING_NONE, null, null, 0, Map.of(), Map.of(), "Sem avaliações ainda", ranking);
        }
        if (n < minDisplay || sumW == 0) {
            String few = n == 1 ? "1 avaliação" : n + " avaliações";
            return new Summary(State.RATING_FEW, null, null, n, Map.of(), Map.of(),
                    few + ", ainda sem nota média", ranking);
        }
        BigDecimal average = oneDecimal(sumWR / sumW);
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            final int s = star;
            histogram.put(star, (int) samples.stream().filter(x -> x.rating() == s).count());
        }
        String label = label(average);
        String accessible = "Nota " + label + " de 5, com base em " + n + " avaliações";
        return new Summary(State.RATED, average, label, n, histogram, dimensions(samples), accessible, ranking);
    }

    /** Cada dimensão só tem média com pelo menos 5 notas nela. */
    private Map<ReviewDimension, BigDecimal> dimensions(List<Sample> samples) {
        Map<ReviewDimension, BigDecimal> out = new EnumMap<>(ReviewDimension.class);
        for (ReviewDimension d : ReviewDimension.values()) {
            double sw = 0;
            double swr = 0;
            int count = 0;
            for (Sample s : samples) {
                Integer v = s.dimensions().get(d);
                if (v != null) {
                    double w = weight(s);
                    sw += w;
                    swr += w * v;
                    count++;
                }
            }
            if (count >= minDisplay && sw > 0) {
                out.put(d, oneDecimal(swr / sw));
            }
        }
        return out;
    }

    double weight(Sample s) {
        double ageDays = Math.max(0, Duration.between(s.experiencedAt(), clock.instant()).toHours() / 24.0);
        double recency = Math.max(recencyFloor, Math.pow(0.5, ageDays / halfLifeDays));
        return s.trustWeight() * recency;
    }

    private static BigDecimal oneDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    /** "4,3": vírgula decimal do pt-BR. */
    static String label(BigDecimal v) {
        return v.toPlainString().replace('.', ',');
    }
}
