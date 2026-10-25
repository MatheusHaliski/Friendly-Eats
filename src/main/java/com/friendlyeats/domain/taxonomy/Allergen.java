package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Alergênicos da Anvisa (RDC 26/2015, consolidada na RDC 727/2022, Anexo III) e as duas advertências, com os códigos da
 * seção 6.2 da taxonomia. Os quatro cereais com glúten são um só item da lista, mas ficam em códigos separados (21
 * vigentes). Os códigos propostos na revisão de 2025 já estão reservados: quando ela entrar em vigor, só muda a
 * {@link Status}. Nunca renomeie nem reaproveite um código.
 *
 * <p>No post (RF9) a lista é autodeclarada por quem publica: as advertências da Anvisa não se aplicam à comida feita e
 * vendida no próprio lugar, e a tela sempre mostra o aviso {@link #NOTICE}.
 */
public enum Allergen {

    TRIGO("Trigo", Kind.ALERGENICO, Status.VIGENTE),
    CENTEIO("Centeio", Kind.ALERGENICO, Status.VIGENTE),
    CEVADA("Cevada", Kind.ALERGENICO, Status.VIGENTE),
    AVEIA("Aveia", Kind.ALERGENICO, Status.VIGENTE),
    CRUSTACEOS("Crustáceos", Kind.ALERGENICO, Status.VIGENTE),
    OVOS("Ovos", Kind.ALERGENICO, Status.VIGENTE),
    PEIXES("Peixes", Kind.ALERGENICO, Status.VIGENTE),
    AMENDOIM("Amendoim", Kind.ALERGENICO, Status.VIGENTE),
    SOJA("Soja", Kind.ALERGENICO, Status.VIGENTE),
    LEITE("Leite (de todos os mamíferos)", Kind.ALERGENICO, Status.VIGENTE),
    AMENDOA("Amêndoa", Kind.ALERGENICO, Status.VIGENTE),
    AVELA("Avelã", Kind.ALERGENICO, Status.VIGENTE),
    CASTANHA_DE_CAJU("Castanha-de-caju", Kind.ALERGENICO, Status.VIGENTE),
    CASTANHA_DO_PARA("Castanha-do-pará / castanha-do-brasil", Kind.ALERGENICO, Status.VIGENTE),
    MACADAMIA("Macadâmia", Kind.ALERGENICO, Status.VIGENTE),
    NOZES("Nozes", Kind.ALERGENICO, Status.VIGENTE),
    NOZ_PECA("Noz-pecã", Kind.ALERGENICO, Status.VIGENTE),
    PISTACHE("Pistache", Kind.ALERGENICO, Status.VIGENTE),
    PINOLI("Pinoli", Kind.ALERGENICO, Status.VIGENTE),
    /** Vigente; a revisão de 2025 propõe a exclusão. */
    CASTANHAS("Castanhas (Castanea spp.)", Kind.ALERGENICO, Status.VIGENTE),
    LATEX_NATURAL("Látex natural", Kind.ALERGENICO, Status.VIGENTE),
    GERGELIM("Gergelim", Kind.ALERGENICO, Status.PROPOSTO),
    TRIGO_SARRACENO("Trigo-sarraceno", Kind.ALERGENICO, Status.PROPOSTO),
    AIPO("Aipo", Kind.ALERGENICO, Status.PROPOSTO),
    TREMOCO("Tremoço", Kind.ALERGENICO, Status.PROPOSTO),
    MOSTARDA("Mostarda", Kind.ALERGENICO, Status.PROPOSTO),
    /** Intolerância, não alergia (RDC 136/2017 → RDC 727/2022). */
    LACTOSE("Contém lactose", Kind.ADVERTENCIA, Status.VIGENTE),
    /** Lei 10.674/2003. */
    GLUTEN("Contém glúten", Kind.ADVERTENCIA, Status.VIGENTE);

    /** Aviso que acompanha toda lista de alergênicos ou advertências. */
    public static final String NOTICE = "Informação declarada por quem publicou, sem conferência da Anvisa nem do "
            + "Friendly Eats. Confirme com o estabelecimento, inclusive sobre contaminação cruzada.";

    /** Alergênico da lista da Anvisa ou advertência (que não é alergênico e fica num campo próprio). */
    public enum Kind { ALERGENICO, ADVERTENCIA }

    /** {@code PROPOSTO} = reservado para a revisão de 2025, ainda não aceito em post. */
    public enum Status { VIGENTE, PROPOSTO }

    /** Nível declarado de um alergênico no prato. */
    public enum Level {
        CONTEM("Contém"),
        PODE_CONTER("Pode conter");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String label;
    private final Kind kind;
    private final Status status;

    Allergen(String label, Kind kind, Status status) {
        this.label = label;
        this.kind = kind;
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isWarning() {
        return kind == Kind.ADVERTENCIA;
    }

    public static Optional<Allergen> fromCode(String code) {
        return Arrays.stream(values()).filter(a -> a.name().equals(code)).findFirst();
    }
}
