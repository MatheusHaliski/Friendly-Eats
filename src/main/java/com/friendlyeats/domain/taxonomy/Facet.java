package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Facetas operacionais do estabelecimento (taxonomia, seção 8; RF11). O dono declara o valor; ausente = "Sem
 * informação". Algumas são fixas por lei (cão de assistência, atendimento prioritário) e não se editam; outras são
 * derivadas: as refeições vêm do horário e o delivery por aplicativo, dos links de delivery.
 * Toda faceta tem rótulo e ícone; o estado sempre sai em texto, nunca só na cor.
 */
public enum Facet {
    // 8.1 atendimento
    SALAO("Consumo no salão", Section.ATENDIMENTO, Values.YES_NO, "utensils"),
    BALCAO("Pedido ou consumo no balcão", Section.ATENDIMENTO, Values.YES_NO, "store"),
    RETIRADA("Retirada / para viagem", Section.ATENDIMENTO, Values.YES_NO, "shopping-bag"),
    RETIRADA_CARRO("Retirada no carro", Section.ATENDIMENTO, Values.YES_NO, "car"),
    DRIVE_THRU("Drive-thru", Section.ATENDIMENTO, Values.YES_NO, "car-front"),
    DELIVERY_PROPRIO("Entrega própria", Section.ATENDIMENTO, Values.YES_NO, "bike"),
    DELIVERY_APP("Entrega por aplicativo", Section.ATENDIMENTO, Values.DERIVED, "smartphone"),
    RESERVA("Reservas", Section.ATENDIMENTO, Values.RESERVATION, "calendar-check"),
    ENCOMENDA("Sob encomenda", Section.ATENDIMENTO, Values.YES_NO, "package"),
    // 8.2 refeições (derivadas do horário)
    CAFE_MANHA("Café da manhã", Section.REFEICOES, Values.DERIVED, "coffee"),
    ALMOCO("Almoço", Section.REFEICOES, Values.DERIVED, "sun"),
    JANTAR("Jantar", Section.REFEICOES, Values.DERIVED, "moon"),
    MADRUGADA("Madrugada", Section.REFEICOES, Values.DERIVED, "moon-star"),
    // 8.3 ambiente e ocasião
    FAMILIA_CRIANCAS("Bom para crianças", Section.AMBIENTE, Values.YES_NO, "baby"),
    GRUPOS("Bom para grupos", Section.AMBIENTE, Values.YES_NO, "users"),
    TRABALHO("Bom para trabalhar", Section.AMBIENTE, Values.YES_NO, "laptop"),
    WIFI("Wi-Fi para clientes", Section.AMBIENTE, Values.YES_NO, "wifi"),
    PET_FRIENDLY("Aceita pets", Section.AMBIENTE, Values.PET, "paw-print"),
    AO_AR_LIVRE("Área ao ar livre", Section.AMBIENTE, Values.YES_NO, "trees"),
    MUSICA_AO_VIVO("Música ao vivo", Section.AMBIENTE, Values.YES_NO, "music"),
    ESPORTES_TV("Transmissão de jogos", Section.AMBIENTE, Values.YES_NO, "tv"),
    LGBTQIA_ACOLHEDOR("Espaço LGBTQIA+ acolhedor", Section.AMBIENTE, Values.YES_NO, "heart-handshake"),
    // 8.4 acessibilidade
    ENTRADA_ACESSIVEL("Entrada acessível (sem degrau ou com rampa)", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "accessibility"),
    MESAS_ACESSIVEIS("Mesas acessíveis", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "armchair"),
    SANITARIO_ACESSIVEL("Sanitário acessível", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "toilet"),
    ESTACIONAMENTO_ACESSIVEL("Vaga reservada", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "square-parking"),
    CARDAPIO_BRAILLE("Cardápio em braille", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "book-open"),
    CARDAPIO_ACESSIVEL_DIGITAL("Cardápio digital acessível", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "monitor-smartphone"),
    ATENDIMENTO_LIBRAS("Atendimento em Libras", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "hand"),
    HORA_SILENCIOSA("Horário com menos estímulo sensorial", Section.ACESSIBILIDADE, Values.YES_PARTIAL_NO, "volume-x"),
    CAO_ASSISTENCIA("Cão-guia ou de assistência sempre permitido", Section.ACESSIBILIDADE, Values.FIXED, "dog"),
    ATENDIMENTO_PRIORITARIO("Atendimento prioritário", Section.ACESSIBILIDADE, Values.FIXED, "user-check"),
    // 8.5 pagamento
    PIX("Pix", Section.PAGAMENTO, Values.YES_NO, "qr-code"),
    CARTAO_CREDITO("Cartão de crédito", Section.PAGAMENTO, Values.YES_NO, "credit-card"),
    CARTAO_DEBITO("Cartão de débito", Section.PAGAMENTO, Values.YES_NO, "credit-card"),
    APROXIMACAO("Aproximação (NFC, carteiras)", Section.PAGAMENTO, Values.YES_NO, "nfc"),
    DINHEIRO("Dinheiro", Section.PAGAMENTO, Values.YES_NO, "banknote"),
    VALE_REFEICAO("Vale-refeição", Section.PAGAMENTO, Values.YES_NO, "ticket",
            EnumSet.of(EstablishmentGroup.RESTAURANTE, EstablishmentGroup.RAPIDO, EstablishmentGroup.BAR,
                    EstablishmentGroup.CAFE_DOCE, EstablishmentGroup.PRODUCAO)),
    VALE_ALIMENTACAO("Vale-alimentação", Section.PAGAMENTO, Values.YES_NO, "shopping-basket",
            EnumSet.of(EstablishmentGroup.VAREJO, EstablishmentGroup.POLO));

    public enum Section {
        ATENDIMENTO("Atendimento"),
        REFEICOES("Refeições"),
        AMBIENTE("Ambiente e ocasião"),
        ACESSIBILIDADE("Acessibilidade"),
        PAGAMENTO("Formas de pagamento");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Valores aceitos; DERIVED e FIXED não vêm do dono. */
    public enum Values {
        YES_NO(List.of("SIM", "NAO")),
        YES_PARTIAL_NO(List.of("SIM", "PARCIAL", "NAO")),
        PET(List.of("DENTRO", "SO_AREA_EXTERNA", "NAO")),
        RESERVATION(List.of("OBRIGATORIA", "RECOMENDADA", "ACEITA", "NAO_ACEITA")),
        DERIVED(List.of("SIM", "NAO")),
        FIXED(List.of("SIM"));

        private final List<String> codes;

        Values(List<String> codes) {
            this.codes = codes;
        }

        public List<String> getCodes() {
            return codes;
        }
    }

    private final String label;
    private final Section section;
    private final Values values;
    private final String lucideIcon;
    private final Set<EstablishmentGroup> groups;

    Facet(String label, Section section, Values values, String lucideIcon) {
        this(label, section, values, lucideIcon, EnumSet.allOf(EstablishmentGroup.class));
    }

    Facet(String label, Section section, Values values, String lucideIcon, Set<EstablishmentGroup> groups) {
        this.label = label;
        this.section = section;
        this.values = values;
        this.lucideIcon = lucideIcon;
        this.groups = groups;
    }

    public String getLabel() { return label; }
    public Section getSection() { return section; }
    public Values getValues() { return values; }
    public String getLucideIcon() { return lucideIcon; }
    public Set<EstablishmentGroup> getGroups() { return groups; }

    /** O dono declara (não é fixa por lei nem derivada). */
    public boolean declarable() {
        return values != Values.DERIVED && values != Values.FIXED;
    }

    public boolean fixed() {
        return values == Values.FIXED;
    }

    public boolean appliesTo(EstablishmentGroup group) {
        return groups.contains(group);
    }

    /** Texto do valor: "Sim", "Parcial", "Aceita pets só na área externa"… */
    public String valueLabel(String value) {
        if (this == PET_FRIENDLY) {
            return switch (value) {
                case "DENTRO" -> "Aceita pets, inclusive no salão";
                case "SO_AREA_EXTERNA" -> "Aceita pets só na área externa";
                default -> "Não aceita pets (cão de assistência sempre entra)";
            };
        }
        if (this == RESERVA) {
            return switch (value) {
                case "OBRIGATORIA" -> "Reserva obrigatória";
                case "RECOMENDADA" -> "Reserva recomendada";
                case "ACEITA" -> "Aceita reservas";
                default -> "Não aceita reservas";
            };
        }
        if (this == CAO_ASSISTENCIA) {
            return "Sempre permitido (Lei 11.126/2005)";
        }
        if (this == ATENDIMENTO_PRIORITARIO) {
            return "Garantido por lei (Lei 10.048/2000)";
        }
        return switch (value) {
            case "SIM" -> "Sim";
            case "PARCIAL" -> "Parcial";
            default -> "Não";
        };
    }

    public static Optional<Facet> fromCode(String code) {
        return Arrays.stream(values()).filter(f -> f.name().equals(code)).findFirst();
    }
}
