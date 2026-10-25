package com.friendlyeats.infrastructure.cache.redis;

/**
 * Fórmula do ranking "em alta" (RF14), a mesma da Orange Social.
 *
 * <p>Visualizações entram em escala logarítmica para que o engajamento ativo (salvar, compartilhar, comentar) ainda
 * pese; o denominador aplica o decaimento por idade no estilo Hacker News, de modo que um post novo em crescimento
 * vença um post antigo com muito engajamento acumulado.
 *
 * <pre>
 * engajamento = log10(1 + viewsÚnicas) * W_VIEWS
 *             + curtidas * W_LIKE + comentários * W_COMMENT + compartilhamentos * W_SHARE + salvos * W_SAVE
 * score       = engajamento / (horasDesdeAPublicação + 2) ^ GRAVITY
 * </pre>
 *
 * <p>Cada interação soma o seu peso bruto no ZSET ({@link #incrementFor}, {@code ZINCRBY}); o recálculo periódico
 * aplica o decaimento com {@link #score}. As visualizações únicas ainda não são contadas (⏳) e entram como zero.
 */
public final class TrendingScoreCalculator {

    static final double VIEW_WEIGHT = 10.0;     // multiplica log10(1 + views)
    static final double LIKE_WEIGHT = 0.8;
    static final double COMMENT_WEIGHT = 1.2;
    static final double SHARE_WEIGHT = 1.5;
    static final double SAVE_WEIGHT = 1.0;
    static final double GRAVITY = 1.6;          // entre 1,5 e 1,8; maior = decai mais rápido

    private TrendingScoreCalculator() {
    }

    public static double score(long uniqueViews, long likes, long comments, long shares, long saves,
                               double hoursSincePublish) {
        if (hoursSincePublish < 0) {
            throw new IllegalArgumentException("hoursSincePublish não pode ser negativo");
        }
        return engagement(uniqueViews, likes, comments, shares, saves) / Math.pow(hoursSincePublish + 2.0, GRAVITY);
    }

    /** Numerador da fórmula, sem decaimento. */
    public static double engagement(long uniqueViews, long likes, long comments, long shares, long saves) {
        return Math.log10(1 + Math.max(0, uniqueViews)) * VIEW_WEIGHT
                + likes * LIKE_WEIGHT
                + comments * COMMENT_WEIGHT
                + shares * SHARE_WEIGHT
                + saves * SAVE_WEIGHT;
    }

    /**
     * Incremento bruto aplicado no ZSET a cada interação. Para views o valor é aproximado (a derivada de log10 em 1
     * view); o recálculo periódico corrige.
     */
    public static double incrementFor(InteractionType interaction) {
        return switch (interaction) {
            case VIEW -> VIEW_WEIGHT / Math.log(10); // ≈ 4,34: d/dx[10·log10(1+x)] em x=0
            case LIKE -> LIKE_WEIGHT;
            case COMMENT -> COMMENT_WEIGHT;
            case SHARE -> SHARE_WEIGHT;
            case SAVE -> SAVE_WEIGHT;
        };
    }

    public enum InteractionType {
        VIEW, LIKE, COMMENT, SHARE, SAVE
    }
}
