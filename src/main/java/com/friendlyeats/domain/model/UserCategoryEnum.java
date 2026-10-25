package com.friendlyeats.domain.model;

import java.util.Arrays;

/**
 * Tipo de conta (quem publica). Aparece como "bolinha" no card: cor + glifo + rótulo, nunca só a cor. A cor aqui é a
 * do tema escuro (fundo #17120D); a variante do tema claro e os glifos ficam em docs/RF1/README.md.
 * Estabelecimento, varejo e oficial publicam como organização só depois de a moderação conferir o selo.
 */
public enum UserCategoryEnum {

    COMENSAL('A', "#48A8FF", "Comensal", "Pessoa que come, avalia e compartilha"),
    CRIADOR('B', "#F7C638", "Criador", "Crítico, blog, vídeo, podcast ou guia gastronômico"),
    CHEF('C', "#D6C5FF", "Chef", "Chef, cozinheiro, confeiteiro, padeiro, barista, sommelier ou nutricionista"),
    ESTABELECIMENTO('D', "#E88F12", "Estabelecimento", "Restaurante, bar, café, padaria, food truck"),
    VAREJO('E', "#EE559F", "Varejo & Marca", "Mercado, empório, indústria, distribuidor ou produtor"),
    COMUNIDADE('F', "#08DABD", "Comunidade", "Confraria, clube, coletivo ou feira gastronômica"),
    /** Preto com anel claro obrigatório no tema escuro (sem o anel o contraste é 1,06:1). */
    OFICIAL('G', "#0C0A07", "Oficial", "Anvisa, vigilância sanitária, Procon e outros órgãos públicos");

    private final char code;
    private final String hexColor;
    private final String label;
    private final String description;

    UserCategoryEnum(char code, String hexColor, String label, String description) {
        this.code = code;
        this.hexColor = hexColor;
        this.label = label;
        this.description = description;
    }

    public char getCode() {
        return code;
    }

    public String getHexColor() {
        return hexColor;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** Organizações: só publicam como tal depois de a moderação conferir CNPJ, CNAE (ou órgão) e representante. */
    public boolean needsSeal() {
        return this == ESTABELECIMENTO || this == VAREJO || this == OFICIAL;
    }

    /** Alerta sanitário e recall (Anvisa, RDC 655/2022) só sai de conta oficial com selo conferido. */
    public boolean canPublishFoodAlerts() {
        return this == OFICIAL;
    }

    public static UserCategoryEnum fromCode(char code) {
        char normalized = Character.toUpperCase(code);
        return Arrays.stream(values())
                .filter(c -> c.code == normalized)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de conta desconhecido: " + code));
    }
}
