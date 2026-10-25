package com.friendlyeats.domain.taxonomy;

import java.util.EnumSet;
import java.util.Set;

/**
 * Notas opcionais de 1 a 5 que explicam a nota geral sem compô-la (RF8). Cada dimensão só vale nos modos em que a
 * pessoa de fato viveu aquilo: ambiente só no local, entrega só no delivery.
 */
public enum ReviewDimension {
    COMIDA("Comida", EnumSet.allOf(VisitMode.class)),
    ATENDIMENTO("Atendimento", EnumSet.of(VisitMode.DINE_IN, VisitMode.TAKEOUT)),
    AMBIENTE("Ambiente", EnumSet.of(VisitMode.DINE_IN)),
    CUSTO_BENEFICIO("Custo-benefício", EnumSet.allOf(VisitMode.class)),
    ENTREGA("Entrega", EnumSet.of(VisitMode.DELIVERY));

    private final String label;
    private final Set<VisitMode> modes;

    ReviewDimension(String label, Set<VisitMode> modes) {
        this.label = label;
        this.modes = modes;
    }

    public String getLabel() {
        return label;
    }

    public boolean appliesTo(VisitMode mode) {
        return modes.contains(mode);
    }
}
