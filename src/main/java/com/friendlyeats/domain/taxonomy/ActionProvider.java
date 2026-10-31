package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Destinos dos botões de ação da página do lugar (RF11). Só links: a plataforma não processa reserva, pedido nem
 * pagamento. Cada provedor tem a lista de domínios aceitos (o domínio ou um subdomínio dele, sempre HTTPS); um link
 * de outro domínio é recusado. ⚖️ A lista de domínios deve ser conferida com cada provedor antes do lançamento.
 */
public enum ActionProvider {
    IFOOD("iFood", Kind.DELIVERY, List.of("ifood.com.br")),
    RAPPI("Rappi", Kind.DELIVERY, List.of("rappi.com.br")),
    AIQFOME("aiqfome", Kind.DELIVERY, List.of("aiqfome.com")),
    NOVENTA_E_NOVE_FOOD("99Food", Kind.DELIVERY, List.of("99app.com")),
    THEFORK("TheFork", Kind.RESERVA, List.of("thefork.com.br", "thefork.com")),
    TAGME("Tagme", Kind.RESERVA, List.of("tagme.com.br")),
    GET_IN("Get In", Kind.RESERVA, List.of("getinapp.com.br")),
    OPENTABLE("OpenTable", Kind.RESERVA, List.of("opentable.com.br", "opentable.com")),
    WHATSAPP("WhatsApp", Kind.WHATSAPP, List.of("wa.me"));

    public enum Kind {
        RESERVA("Reserva"),
        DELIVERY("Delivery"),
        WHATSAPP("WhatsApp");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String label;
    private final Kind kind;
    private final List<String> domains;

    ActionProvider(String label, Kind kind, List<String> domains) {
        this.label = label;
        this.kind = kind;
        this.domains = domains;
    }

    public String getLabel() { return label; }
    public Kind getKind() { return kind; }
    public List<String> getDomains() { return domains; }

    /** Host igual a um dos domínios ou subdomínio dele ({@code www.ifood.com.br}); comparação exata, em minúsculas. */
    public boolean acceptsHost(String host) {
        return host != null && domains.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }

    public static Optional<ActionProvider> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }
}
