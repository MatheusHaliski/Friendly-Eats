package com.friendlyeats.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Finalidades opcionais de tratamento (RF3). Todas nascem desligadas (Privacy by Default) e cada mudança vira uma
 * linha em {@code consent_event}. {@link MinorRule}: até que idade a finalidade fica travada. Menor de 16 não liga
 * perfilamento, publicidade dirigida nem rastreio de localização (ECA Digital); estilos alimentares (saúde,
 * religião) ficam travados para menor de 18 enquanto não houver o consentimento do responsável (LGPD Art. 14).
 */
public enum ConsentPurpose {
    LOCATION_TRACKING("Guardar minha localização",
            "Guarda a última localização para \"perto de mim\" e alertas sanitários da sua região. A busca pontual por "
                    + "raio funciona sem isso.", "ART_7_I", false, MinorRule.UNDER_16),
    PERSONALIZED_FEED("Feed e busca pelo meu gosto",
            "Ordena o feed e a busca pelas suas culinárias favoritas e pelo que você avaliou.", "ART_7_I", false, MinorRule.UNDER_16),
    SENSITIVE_PROFILE_DATA("Estilos alimentares no perfil",
            "Guarda estilos que podem revelar saúde (sem glúten, sem lactose) ou religião (halal, kosher). "
                    + "Desligar apaga os estilos salvos.", "ART_11_I", true, MinorRule.UNDER_18),
    MARKETING("Ofertas de estabelecimentos e marcas",
            "Envia promoções e novidades de lugares e marcas por e-mail e notificação.", "ART_7_I", false, MinorRule.UNDER_16),
    THIRD_PARTY_SHARING("Compartilhar com parceiros",
            "Compartilha nome e contato com parceiros de reserva e delivery quando você usa esses serviços. "
                    + "Dado de saúde nunca é compartilhado.", "ART_7_I", false, MinorRule.NONE);

    private final String label;
    private final String description;
    private final String legalBasis;
    private final boolean sensitive;
    private final MinorRule minorRule;

    /** Idade abaixo da qual a pessoa não pode ligar a finalidade; desligar é sempre livre. */
    public enum MinorRule { NONE, UNDER_16, UNDER_18 }

    ConsentPurpose(String label, String description, String legalBasis, boolean sensitive, MinorRule minorRule) {
        this.label = label;
        this.description = description;
        this.legalBasis = legalBasis;
        this.sensitive = sensitive;
        this.minorRule = minorRule;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** Base legal gravada no evento: Art. 7º, I ou, para dado sensível, Art. 11, I. */
    public String getLegalBasis() {
        return legalBasis;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public MinorRule getMinorRule() {
        return minorRule;
    }

    public static Optional<ConsentPurpose> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }
}
