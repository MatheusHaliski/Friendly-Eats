package com.friendlyeats.domain.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Os 7 tipos de post de comida (RF9; docs/TAXONOMIA_FRIENDLY_EATS.md, seção 13). A borda do card muda de
 * <b>forma</b>, não de cor (só o alerta tem a faixa hazard), e cada tipo tem o seu ícone Lucide e o rótulo em texto.
 *
 * <p>Quem publica: {@link #denialFor} confere o tipo de conta e o selo; a posse do lugar (prato do dia e promoção de
 * estabelecimento) e o chip Publicidade do criador são conferidos no serviço, que conhece o lugar e o pedido.
 */
public enum PostKind {

    FOTO_PRATO("Foto de prato", "camera", "SEM_MOLDURA", FeedType.POST,
            "Qualquer conta ativa (organizações depois do selo)."),
    RECEITA("Receita", "book-open", "TRACEJADA", FeedType.POST,
            "Qualquer conta ativa (organizações depois do selo)."),
    PERGUNTA("Pergunta", "message-circle-question", "BALAO", FeedType.POST,
            "Qualquer conta ativa, exceto a Oficial."),
    PRATO_DO_DIA("Prato do dia", "hand-platter", "DUPLA", FeedType.POST,
            "Só conta de Estabelecimento verificada, sobre um lugar dela."),
    PROMOCAO("Promoção", "badge-percent", "ETIQUETA", FeedType.POST,
            "Estabelecimento ou Varejo & Marca verificados, sobre um lugar seu; Criador só com o chip Publicidade."),
    EVENTO("Evento", "calendar-days", "DATA", FeedType.POST,
            "Estabelecimento, Varejo & Marca, Comunidade ou Chef."),
    ALERTA_SANITARIO("Alerta sanitário", "triangle-alert", "HAZARD", FeedType.ALERT,
            "Só conta Oficial verificada (Anvisa, vigilância sanitária, Procon).");

    private static final Set<UserCategoryEnum> EVENT_HOSTS = Set.of(UserCategoryEnum.ESTABELECIMENTO,
            UserCategoryEnum.VAREJO, UserCategoryEnum.COMUNIDADE, UserCategoryEnum.CHEF);

    private final String label;
    private final String lucideIcon;
    private final String border;
    private final FeedType feed;
    private final String whoCanPublish;

    PostKind(String label, String lucideIcon, String border, FeedType feed, String whoCanPublish) {
        this.label = label;
        this.lucideIcon = lucideIcon;
        this.border = border;
        this.feed = feed;
        this.whoCanPublish = whoCanPublish;
    }

    public String getLabel() {
        return label;
    }

    public String getLucideIcon() {
        return lucideIcon;
    }

    /** Forma da borda do card: {@code SEM_MOLDURA}, {@code TRACEJADA}, {@code BALAO}, {@code DUPLA}… */
    public String getBorder() {
        return border;
    }

    public FeedType feed() {
        return feed;
    }

    public String getWhoCanPublish() {
        return whoCanPublish;
    }

    /** Prato do dia, promoção e evento saem do feed sozinhos (às 23h59 do dia ou no fim da vigência). */
    public boolean expires() {
        return this == PRATO_DO_DIA || this == PROMOCAO || this == EVENTO;
    }

    /** Precisa de um lugar da própria conta: prato do dia sempre; promoção quando é o dono que anuncia. */
    public boolean needsOwnedPlace(UserCategoryEnum author) {
        return this == PRATO_DO_DIA
                || (this == PROMOCAO && (author == UserCategoryEnum.ESTABELECIMENTO || author == UserCategoryEnum.VAREJO));
    }

    /**
     * Pode publicar este tipo? {@code null} = pode; senão, a mensagem do {@code 403}. Organizações (estabelecimento,
     * varejo e oficial) só publicam depois de a moderação conferir o selo.
     */
    public String denialFor(UserCategoryEnum author, CategoryVerification seal) {
        if (author.needsSeal() && seal != CategoryVerification.VERIFIED) {
            return "Sua conta de " + author.getLabel() + " ainda está em verificação: você já pode ler e interagir, "
                    + "e publica depois do selo.";
        }
        return switch (this) {
            case FOTO_PRATO, RECEITA -> null;
            case PERGUNTA -> author == UserCategoryEnum.OFICIAL
                    ? "Conta Oficial não publica pergunta: publique um alerta sanitário ou responda nos comentários."
                    : null;
            case PRATO_DO_DIA -> author == UserCategoryEnum.ESTABELECIMENTO ? null
                    : "Prato do dia é só de conta de Estabelecimento, sobre um lugar dela.";
            case PROMOCAO -> author == UserCategoryEnum.ESTABELECIMENTO || author == UserCategoryEnum.VAREJO
                    || author == UserCategoryEnum.CRIADOR ? null
                    : "Promoção é publicada pelo estabelecimento ou pela marca; criador, só com o chip Publicidade.";
            case EVENTO -> EVENT_HOSTS.contains(author) ? null
                    : "Evento é publicado por estabelecimento, varejo, comunidade ou chef.";
            case ALERTA_SANITARIO -> author.canPublishFoodAlerts() ? null
                    : "Alerta sanitário só sai de conta Oficial verificada (Anvisa, vigilância sanitária, Procon).";
        };
    }

    public static Optional<PostKind> fromCode(String code) {
        return Arrays.stream(values()).filter(k -> k.name().equals(code)).findFirst();
    }
}
