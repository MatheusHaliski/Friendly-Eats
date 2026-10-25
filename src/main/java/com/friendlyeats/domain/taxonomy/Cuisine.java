package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Culinária: de qual tradição vem a comida (RF7; docs/TAXONOMIA_FRIENDLY_EATS.md). Código estável — nunca renomeado
 * nem reaproveitado; o rótulo pode mudar. Cada culinária específica tem um pai (a genérica da região ou da macro), e
 * marcar uma culinária junto com um ancestral dela é recusado: BAIANA já implica NORDESTINA e BRASILEIRA.
 */
public enum Cuisine {
    // Brasil (região IBGE)
    BRASILEIRA("Brasileira", CuisineMacro.BRASIL, null, null),
    MINEIRA("Mineira", CuisineMacro.BRASIL, "BRASILEIRA", "SE"),
    PAULISTANA("Paulista / paulistana", CuisineMacro.BRASIL, "BRASILEIRA", "SE"),
    CAPIXABA("Capixaba", CuisineMacro.BRASIL, "BRASILEIRA", "SE"),
    CAICARA("Caiçara", CuisineMacro.BRASIL, "BRASILEIRA", "SE"),
    NORDESTINA("Nordestina", CuisineMacro.BRASIL, "BRASILEIRA", "NE"),
    BAIANA("Baiana", CuisineMacro.BRASIL, "NORDESTINA", "NE"),
    AMAZONICA("Amazônica / nortista", CuisineMacro.BRASIL, "BRASILEIRA", "N"),
    PARAENSE("Paraense", CuisineMacro.BRASIL, "AMAZONICA", "N"),
    GOIANA("Goiana", CuisineMacro.BRASIL, "BRASILEIRA", "CO"),
    PANTANEIRA("Pantaneira", CuisineMacro.BRASIL, "BRASILEIRA", "CO"),
    GAUCHA("Gaúcha", CuisineMacro.BRASIL, "BRASILEIRA", "S"),
    // América Latina e Caribe
    LATINO_AMERICANA("Latino-americana", CuisineMacro.AMERICA_LATINA, null, null),
    ARGENTINA("Argentina", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    URUGUAIA("Uruguaia", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    PERUANA("Peruana", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    NIKKEI("Nikkei", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    MEXICANA("Mexicana", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    COLOMBIANA("Colombiana", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    VENEZUELANA("Venezuelana", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    BOLIVIANA("Boliviana", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    CHILENA("Chilena", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    CARIBENHA("Caribenha", CuisineMacro.AMERICA_LATINA, "LATINO_AMERICANA", null),
    // América do Norte
    NORTE_AMERICANA("Norte-americana", CuisineMacro.AMERICA_NORTE, null, null),
    TEX_MEX("Tex-Mex", CuisineMacro.AMERICA_NORTE, "NORTE_AMERICANA", null),
    SUL_DOS_EUA("Sul dos EUA", CuisineMacro.AMERICA_NORTE, "NORTE_AMERICANA", null),
    HAVAIANA("Havaiana", CuisineMacro.AMERICA_NORTE, "NORTE_AMERICANA", null),
    // Europa
    EUROPEIA("Europeia", CuisineMacro.EUROPA, null, null),
    ITALIANA("Italiana", CuisineMacro.EUROPA, "EUROPEIA", null),
    PORTUGUESA("Portuguesa", CuisineMacro.EUROPA, "EUROPEIA", null),
    FRANCESA("Francesa", CuisineMacro.EUROPA, "EUROPEIA", null),
    ESPANHOLA("Espanhola", CuisineMacro.EUROPA, "EUROPEIA", null),
    ALEMA("Alemã", CuisineMacro.EUROPA, "EUROPEIA", null),
    GREGA("Grega", CuisineMacro.EUROPA, "EUROPEIA", null),
    MEDITERRANEA("Mediterrânea", CuisineMacro.EUROPA, "EUROPEIA", null),
    BRITANICA_IRLANDESA("Britânica e irlandesa", CuisineMacro.EUROPA, "EUROPEIA", null),
    LESTE_EUROPEIA("Leste europeu", CuisineMacro.EUROPA, "EUROPEIA", null),
    // Oriente Médio e Norte da África
    ORIENTE_MEDIO("Médio-oriental", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, null, null),
    ARABE("Árabe", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    TURCA("Turca", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    PERSA("Persa / iraniana", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    JUDAICA_ISRAELENSE("Judaica e israelense", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    MARROQUINA("Marroquina / magrebina", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    ARMENIA("Armênia", CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, "ORIENTE_MEDIO", null),
    // África Subsaariana
    AFRICANA("Africana", CuisineMacro.AFRICA, null, null),
    ANGOLANA("Angolana", CuisineMacro.AFRICA, "AFRICANA", null),
    ETIOPE("Etíope", CuisineMacro.AFRICA, "AFRICANA", null),
    SENEGALESA("Senegalesa", CuisineMacro.AFRICA, "AFRICANA", null),
    // Ásia
    ASIATICA("Asiática", CuisineMacro.ASIA, null, null),
    JAPONESA("Japonesa", CuisineMacro.ASIA, "ASIATICA", null),
    CHINESA("Chinesa", CuisineMacro.ASIA, "ASIATICA", null),
    TAIWANESA("Taiwanesa", CuisineMacro.ASIA, "ASIATICA", null),
    COREANA("Coreana", CuisineMacro.ASIA, "ASIATICA", null),
    TAILANDESA("Tailandesa", CuisineMacro.ASIA, "ASIATICA", null),
    VIETNAMITA("Vietnamita", CuisineMacro.ASIA, "ASIATICA", null),
    INDIANA("Indiana", CuisineMacro.ASIA, "ASIATICA", null);

    private final String label;
    private final CuisineMacro macro;
    private final String parentCode;
    private final String ibgeRegion;

    Cuisine(String label, CuisineMacro macro, String parentCode, String ibgeRegion) {
        this.label = label;
        this.macro = macro;
        this.parentCode = parentCode;
        this.ibgeRegion = ibgeRegion;
    }

    public String getLabel() {
        return label;
    }

    public CuisineMacro getMacro() {
        return macro;
    }

    /** Região IBGE (N, NE, CO, SE, S) das culinárias regionais brasileiras; nulo nas demais. */
    public String getIbgeRegion() {
        return ibgeRegion;
    }

    public Optional<Cuisine> parent() {
        return Optional.ofNullable(parentCode).map(Cuisine::valueOf);
    }

    /** Genérica da macro (BRASILEIRA, EUROPEIA, ASIATICA…): sem pai. */
    public boolean isMacroGeneric() {
        return parentCode == null;
    }

    /** {@code this} é pai, avô… de {@code other}? (BRASILEIRA é ancestral de BAIANA.) */
    public boolean isAncestorOf(Cuisine other) {
        for (Optional<Cuisine> p = other.parent(); p.isPresent(); p = p.get().parent()) {
            if (p.get() == this) {
                return true;
            }
        }
        return false;
    }

    public static Optional<Cuisine> fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equals(code)).findFirst();
    }
}
