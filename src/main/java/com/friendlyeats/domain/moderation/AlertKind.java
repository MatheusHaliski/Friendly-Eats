package com.friendlyeats.domain.moderation;

import java.util.Locale;
import java.util.Optional;

/**
 * Alerta ao consumidor na página do lugar (RF13 · RC-SEG-05). Texto factual e escrito, com ícone e sem depender de
 * cor; acusação sem prova guardada traz risco de dano à imagem da empresa ⚖️.
 */
public enum AlertKind {
    AVALIACAO_PAGA("Avaliações pagas ou trocadas",
            "Encontramos indícios de avaliações pagas ou trocadas por vantagem neste lugar e removemos as envolvidas."),
    ATIVIDADE_SUSPEITA("Atividade fora do padrão",
            "Este lugar recebeu muitas avaliações em pouco tempo, várias de contas novas. Elas estão em análise e fora "
                    + "da nota até a decisão."),
    ATENCAO_PUBLICA("Atenção pública",
            "Este lugar virou assunto fora do app e recebeu uma onda de avaliações. Pausamos novas avaliações por "
                    + "alguns dias para conferir se são experiências próprias.");

    private final String label;
    private final String defaultMessage;

    AlertKind(String label, String defaultMessage) {
        this.label = label;
        this.defaultMessage = defaultMessage;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public static Optional<AlertKind> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
