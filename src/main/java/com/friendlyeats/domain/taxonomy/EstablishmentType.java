package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.CAFE_DOCE;
import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.POLO;
import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.PRODUCAO;
import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.RAPIDO;
import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.RESTAURANTE;
import static com.friendlyeats.domain.taxonomy.EstablishmentGroup.VAREJO;

/**
 * Tipo de estabelecimento: o que o lugar é e como serve (RF6). Nenhum tipo carrega culinária — pizzaria é
 * RESTAURANTE_A_LA_CARTE + PIZZA. O CNAE típico serve à verificação do selo (divergência manda para revisão, não
 * bloqueia). Tipos com foco em bebida alcoólica só aparecem para maiores de 18.
 */
public enum EstablishmentType {
    RESTAURANTE_A_LA_CARTE("Restaurante (à la carte)", RESTAURANTE, false, "5611-2/01"),
    RESTAURANTE_QUILO("Self-service / por quilo", RESTAURANTE, false, "5611-2/01"),
    RESTAURANTE_BUFFET("Buffet de preço fixo", RESTAURANTE, false, "5611-2/01"),
    RESTAURANTE_RODIZIO("Rodízio", RESTAURANTE, false, "5611-2/01"),
    RESTAURANTE_PF("Comida caseira / PF / executivo", RESTAURANTE, false, "5611-2/01"),
    RESTAURANTE_ALTA_GASTRONOMIA("Alta gastronomia", RESTAURANTE, false, "5611-2/01"),
    BISTRO("Bistrô", RESTAURANTE, false, "5611-2/01"),
    FAST_FOOD("Fast food", RAPIDO, false, "5611-2/03"),
    FAST_CASUAL("Fast casual", RAPIDO, false, "5611-2/01", "5611-2/03"),
    LANCHONETE("Lanchonete / pastelaria", RAPIDO, false, "5611-2/03"),
    QUIOSQUE("Quiosque", RAPIDO, false, "5611-2/03"),
    FOOD_TRUCK("Food truck / trailer", RAPIDO, false, "5612-1/00"),
    AMBULANTE("Comida de rua", RAPIDO, false, "5612-1/00"),
    BAR("Bar", EstablishmentGroup.BAR, true, "5611-2/04", "5611-2/05"),
    BOTECO("Boteco", EstablishmentGroup.BAR, true, "5611-2/04"),
    GASTROBAR("Gastrobar / pub", EstablishmentGroup.BAR, true, "5611-2/01", "5611-2/04"),
    BAR_COQUETELARIA("Coquetelaria", EstablishmentGroup.BAR, true, "5611-2/04"),
    BAR_VINHOS("Bar de vinhos", EstablishmentGroup.BAR, true, "5611-2/04"),
    CERVEJARIA("Cervejaria / choperia", EstablishmentGroup.BAR, true, "1113-5/02", "5611-2/04"),
    CAFETERIA("Cafeteria", CAFE_DOCE, false, "5611-2/03"),
    PADARIA("Padaria", CAFE_DOCE, false, "1091-1/02", "4721-1/02", "5611-2/03"),
    CONFEITARIA("Confeitaria / doceria", CAFE_DOCE, false, "1091-1/02", "4721-1/04"),
    SORVETERIA("Sorveteria / gelateria", CAFE_DOCE, false, "5611-2/03", "1053-8/00"),
    ACAITERIA_SUCOS("Açaiteria / casa de sucos", CAFE_DOCE, false, "5611-2/03"),
    CASA_CHA("Casa de chá", CAFE_DOCE, false, "5611-2/03"),
    DARK_KITCHEN("Dark kitchen (só delivery)", PRODUCAO, false, "5620-1/04", "5611-2/01"),
    MARMITARIA("Marmitaria", PRODUCAO, false, "5620-1/04"),
    BUFE_EVENTOS("Bufê para eventos", PRODUCAO, false, "5620-1/02"),
    COZINHA_ARTESANAL("Produção artesanal / por encomenda", PRODUCAO, false, "1091-1/02"),
    VINICOLA_DESTILARIA("Vinícola / destilaria", PRODUCAO, true, "1112-7/00", "1111-9/01"),
    SUPERMERCADO("Supermercado", VAREJO, false, "4711-3/01", "4711-3/02"),
    ATACAREJO("Atacarejo", VAREJO, false, "4711-3/01", "4639-7/01"),
    MERCEARIA("Mercearia / minimercado", VAREJO, false, "4712-1/00"),
    LOJA_CONVENIENCIA("Loja de conveniência", VAREJO, false, "4729-6/02"),
    EMPORIO("Empório / delicatessen", VAREJO, false, "4729-6/99", "4721-1/03"),
    ACOUGUE("Açougue", VAREJO, false, "4722-9/01"),
    PEIXARIA("Peixaria", VAREJO, false, "4722-9/02"),
    HORTIFRUTI("Hortifrúti / sacolão", VAREJO, false, "4724-5/00"),
    QUEIJARIA_FRIOS("Queijaria / frios", VAREJO, false, "4721-1/03"),
    DOCES_BALAS("Bomboniere / chocolateria", VAREJO, false, "4721-1/04"),
    ADEGA("Adega / loja de bebidas", VAREJO, true, "4723-7/00"),
    LOJA_NATURAL("Produtos naturais / granel", VAREJO, false, "4729-6/99"),
    CAFE_TORREFACAO("Torrefação / café em grão", VAREJO, false, "1081-3/02", "4729-6/99"),
    MERCADO_MUNICIPAL("Mercado municipal", POLO, false),
    FEIRA_LIVRE("Feira livre", POLO, false),
    PRACA_ALIMENTACAO("Praça de alimentação", POLO, false),
    FOOD_PARK("Food park / polo gastronômico", POLO, false),
    VENDA_PRODUTOR("Venda direta do produtor", POLO, false);

    private final String label;
    private final EstablishmentGroup group;
    private final boolean alcoholFocused;
    private final List<String> typicalCnae;

    EstablishmentType(String label, EstablishmentGroup group, boolean alcoholFocused, String... typicalCnae) {
        this.label = label;
        this.group = group;
        this.alcoholFocused = alcoholFocused;
        this.typicalCnae = List.of(typicalCnae);
    }

    public String getLabel() {
        return label;
    }

    public EstablishmentGroup getGroup() {
        return group;
    }

    /** Foco em bebida alcoólica: a página só aparece para maiores de 18 (Termos de Uso). */
    public boolean isAlcoholFocused() {
        return alcoholFocused;
    }

    /** Subclasses CNAE típicas (CNAE 2.3); vazio nos polos, em que cada box tem o seu. */
    public List<String> getTypicalCnae() {
        return typicalCnae;
    }

    public static Optional<EstablishmentType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.name().equals(code)).findFirst();
    }
}
