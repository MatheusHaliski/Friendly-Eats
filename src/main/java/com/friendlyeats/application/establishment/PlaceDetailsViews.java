package com.friendlyeats.application.establishment;

import com.friendlyeats.application.establishment.EstablishmentViews.ActionView;
import com.friendlyeats.application.establishment.EstablishmentViews.ChargeLine;
import com.friendlyeats.application.establishment.EstablishmentViews.ChargesView;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.establishment.EstablishmentViews.CouvertView;
import com.friendlyeats.application.establishment.EstablishmentViews.DayHoursView;
import com.friendlyeats.application.establishment.EstablishmentViews.FacetSectionView;
import com.friendlyeats.application.establishment.EstablishmentViews.FacetView;
import com.friendlyeats.application.establishment.EstablishmentViews.HoursView;
import com.friendlyeats.application.establishment.EstablishmentViews.OpenStatusView;
import com.friendlyeats.application.establishment.EstablishmentViews.PaymentAdjustmentView;
import com.friendlyeats.application.establishment.EstablishmentViews.RangeView;
import com.friendlyeats.application.establishment.EstablishmentViews.SpecialDayView;
import com.friendlyeats.application.establishment.EstablishmentViews.TemporaryClosureView;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.OpeningHours;
import com.friendlyeats.domain.model.PlaceDetails;
import com.friendlyeats.domain.model.PlaceTimeZone;
import com.friendlyeats.domain.taxonomy.ActionProvider;
import com.friendlyeats.domain.taxonomy.Facet;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Monta as partes da página do lugar que o RF11 acrescenta: "aberto agora", horários, cobranças com os textos fixos,
 * facetas (declaradas, fixas por lei e derivadas) e botões de ação. Tudo calculado no fuso do lugar.
 */
final class PlaceDetailsViews {

    static final String CHARGES_NOTICE = "A página não substitui os preços e avisos afixados na entrada do lugar "
            + "(Decreto 5.903/2006, art. 8º, § 2º).";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] SHORT_DAY = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};

    private PlaceDetailsViews() {
    }

    static ZoneId zone(Establishment e) {
        return PlaceTimeZone.of(e.getUf(), e.getMunicipalityIbge());
    }

    static OpenStatusView openStatus(Establishment e, Instant now) {
        ZoneId zone = zone(e);
        OpeningHours.Status s = OpeningHours.from(e.getOpeningHours()).status(now, zone,
                e.getStatus() == Establishment.Status.CLOSED, e.isTemporarilyClosed(), e.getReopensOn());
        return new OpenStatusView(s.state().name(), s.open(), s.label(),
                s.closesAt() == null ? null : s.closesAt().format(LOCAL),
                s.opensAt() == null ? null : s.opensAt().format(LOCAL), s.note(), zone.getId());
    }

    static HoursView hours(Establishment e, Instant now) {
        ZoneId zone = zone(e);
        LocalDate today = now.atZone(zone).toLocalDate();
        OpeningHours h = OpeningHours.from(e.getOpeningHours());
        List<DayHoursView> weekly = new ArrayList<>();
        if (h.weeklyInformed()) {
            for (DayOfWeek d : DayOfWeek.values()) {
                List<RangeView> ranges = h.weekly(d).stream().map(PlaceDetailsViews::range).toList();
                String name = OpeningHours.dayName(d);
                weekly.add(new DayHoursView(d.name(), Character.toUpperCase(name.charAt(0)) + name.substring(1), ranges,
                        ranges.isEmpty(), d == today.getDayOfWeek()));
            }
        }
        List<SpecialDayView> special = h.upcoming(today).stream()
                .map(s -> new SpecialDayView(s.date().toString(), s.date().format(DateTimeFormatter.ofPattern("dd/MM"))
                        + " (" + SHORT_DAY[s.date().getDayOfWeek().ordinal()] + ")", s.closed(),
                        s.ranges().stream().map(PlaceDetailsViews::range).toList(), s.note()))
                .toList();
        return new HoursView(zone.getId(), h.weeklyInformed(), weekly, special,
                new TemporaryClosureView(e.isTemporarilyClosed(),
                        e.getReopensOn() == null ? null : e.getReopensOn().toString()));
    }

    private static RangeView range(OpeningHours.Range r) {
        return new RangeView(r.opensText(), r.closesText(), r.overnight());
    }

    // ------------------------------------------------------------ cobranças

    static ChargesView charges(Establishment e) {
        PlaceDetails.ChargesData c = e.getCharges();
        if (c == null) {
            return new ChargesView(null, null, List.of(), List.of(), null, null, CHARGES_NOTICE);
        }
        List<ChargeLine> lines = new ArrayList<>();
        if (c.serviceFeePercent() != null) {
            lines.add(new ChargeLine("TAXA_SERVICO", "Taxa de serviço",
                    "Taxa de serviço de " + c.serviceFeePercent() + "% (opcional)",
                    "É gorjeta: você paga só se quiser (Lei 13.419/2017)."));
        }
        CouvertView couvert = null;
        if (c.couvert() != null) {
            PlaceDetails.CouvertData cv = c.couvert();
            List<Coded> days = (cv.days() == null ? List.<String>of() : cv.days()).stream()
                    .map(DayOfWeek::valueOf).sorted()
                    .map(d -> new Coded(d.name(), OpeningHours.dayName(d), null)).toList();
            String value = PriceScale.brl(cv.cents());
            if ("ARTISTICO".equals(cv.kind())) {
                couvert = new CouvertView(cv.kind(), "Couvert artístico", cv.cents(), days);
                lines.add(new ChargeLine("COUVERT", "Couvert artístico",
                        "Couvert artístico de " + value + " por pessoa, só nos dias com apresentação ao vivo ("
                                + String.join(", ", days.stream().map(Coded::label).toList()) + ")",
                        "Só pode ser cobrado com aviso na entrada, com o preço e a programação."));
            } else {
                couvert = new CouvertView(cv.kind(), "Couvert (pão, petiscos)", cv.cents(), days);
                lines.add(new ChargeLine("COUVERT", "Couvert", "Couvert (pão, petiscos) de " + value
                        + " por pessoa, cobrado só se você aceitar",
                        "Se trouxerem sem você pedir e sem avisar o preço, é amostra grátis."));
            }
        }
        List<PaymentAdjustmentView> adjustments = new ArrayList<>();
        for (PlaceDetails.PaymentAdjustmentData a : c.paymentAdjustments() == null
                ? List.<PlaceDetails.PaymentAdjustmentData>of() : c.paymentAdjustments()) {
            Facet method = Facet.valueOf(a.method());
            String text = a.percent() + "% de " + ("DESCONTO".equals(a.kind()) ? "desconto" : "acréscimo")
                    + " " + paymentPhrase(method);
            adjustments.add(new PaymentAdjustmentView(a.method(), method.getLabel(), a.kind(), a.percent(), text));
            lines.add(new ChargeLine("FORMA_PAGAMENTO", "Por forma de pagamento", text,
                    "Diferença de preço por forma de pagamento informada (Lei 10.962/2004, art. 5º-A)."));
        }
        Instant at = e.getChargesDeclaredAt();
        return new ChargesView(c.serviceFeePercent(), couvert, adjustments, lines, at,
                at == null ? null : "Declarado pelo estabelecimento em " + at.atZone(zone(e)).format(DATE_BR),
                CHARGES_NOTICE);
    }

    /** "no Pix", "em dinheiro", "no cartão de crédito"… */
    static String paymentPhrase(Facet method) {
        return switch (method) {
            case PIX -> "no Pix";
            case DINHEIRO -> "em dinheiro";
            case CARTAO_CREDITO -> "no cartão de crédito";
            case CARTAO_DEBITO -> "no cartão de débito";
            case APROXIMACAO -> "no pagamento por aproximação";
            case VALE_REFEICAO -> "no vale-refeição";
            case VALE_ALIMENTACAO -> "no vale-alimentação";
            default -> "com " + method.getLabel();
        };
    }

    // ------------------------------------------------------------ facetas

    static List<FacetSectionView> facets(Establishment e) {
        Map<Facet, FacetView> items = new LinkedHashMap<>();
        Map<String, String> declared = e.getFacets();
        for (Facet f : Facet.values()) {
            String v = declared.get(f.name());
            if (v != null && f.declarable()) {
                items.put(f, facet(f, v, "DECLARADO_DONO", "Declarado pelo estabelecimento"));
            } else if (f.fixed()) {
                items.put(f, facet(f, "SIM", "LEI", "Garantido por lei"));
            }
        }
        PlaceDetails.ActionsData actions = e.getActionLinks();
        if (actions != null && actions.links() != null && actions.links().stream()
                .anyMatch(l -> ActionProvider.valueOf(l.provider()).getKind() == ActionProvider.Kind.DELIVERY)) {
            items.put(Facet.DELIVERY_APP, facet(Facet.DELIVERY_APP, "SIM", "LINKS", "Pelos links de delivery"));
        }
        OpeningHours.from(e.getOpeningHours()).meals().forEach((code, yes) -> {
            if (yes) {
                Facet f = Facet.valueOf(code);
                items.put(f, facet(f, "SIM", "HORARIO", "Pelo horário de funcionamento"));
            }
        });
        List<FacetSectionView> out = new ArrayList<>();
        for (Facet.Section section : Facet.Section.values()) {
            List<FacetView> list = items.entrySet().stream().filter(x -> x.getKey().getSection() == section)
                    .sorted(Comparator.comparingInt(x -> x.getKey().ordinal())).map(Map.Entry::getValue).toList();
            if (!list.isEmpty()) {
                out.add(new FacetSectionView(section.name(), section.getLabel(), list));
            }
        }
        return out;
    }

    private static FacetView facet(Facet f, String value, String origin, String originLabel) {
        return new FacetView(f.name(), f.getLabel(), value, f.valueLabel(value), f.getLucideIcon(), origin, originLabel);
    }

    // ------------------------------------------------------------ botões de ação

    /**
     * Reserva, delivery (o preferido primeiro; os demais em ordem embaralhada, estável no dia, para não favorecer
     * ninguém) e WhatsApp.
     */
    static List<ActionView> actions(Establishment e, Instant now) {
        PlaceDetails.ActionsData a = e.getActionLinks();
        if (a == null) {
            return List.of();
        }
        LocalDate today = now.atZone(zone(e)).toLocalDate();
        List<ActionView> out = new ArrayList<>();
        List<PlaceDetails.ActionLinkData> links = a.links() == null ? List.of() : a.links();
        links.stream().filter(l -> ActionProvider.valueOf(l.provider()).getKind() == ActionProvider.Kind.RESERVA)
                .forEach(l -> out.add(link(l)));
        links.stream().filter(l -> ActionProvider.valueOf(l.provider()).getKind() == ActionProvider.Kind.DELIVERY)
                .sorted(Comparator.comparing((PlaceDetails.ActionLinkData l) -> !l.preferred())
                        .thenComparingInt(l -> Objects.hash(e.getId(), today.toEpochDay(), l.provider())))
                .forEach(l -> out.add(link(l)));
        if (a.whatsapp() != null) {
            out.add(new ActionView("WHATSAPP", "WHATSAPP", "WhatsApp", "Conversar no WhatsApp",
                    "https://wa.me/" + a.whatsapp(), false,
                    "Abre o WhatsApp numa conversa com o estabelecimento; o Friendly Eats não vê a conversa.",
                    "+" + a.whatsapp()));
        }
        return out;
    }

    private static ActionView link(PlaceDetails.ActionLinkData l) {
        ActionProvider p = ActionProvider.valueOf(l.provider());
        boolean reservation = p.getKind() == ActionProvider.Kind.RESERVA;
        return new ActionView(p.getKind().name(), p.name(), p.getLabel(),
                (reservation ? "Reservar pelo " : "Pedir pelo ") + p.getLabel(), l.url(), l.preferred(),
                reservation ? "Você vai sair do Friendly Eats. A reserva é feita com " + p.getLabel() + "."
                        : "Você vai sair do Friendly Eats. Pedido e pagamento são com " + p.getLabel() + ".",
                null);
    }
}
