package com.friendlyeats.application.establishment;

import com.friendlyeats.application.establishment.EstablishmentDrafts.ActionLinkDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ActionsDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ChargesDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.CouvertDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.HoursDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.PaymentAdjustmentDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.RangeDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.SpecialDayDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.TemporaryClosureDraft;
import com.friendlyeats.domain.model.OpeningHours;
import com.friendlyeats.domain.model.PlaceDetails;
import com.friendlyeats.domain.model.PlaceTimeZone;
import com.friendlyeats.domain.taxonomy.ActionProvider;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.Facet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Página do lugar (RF11): horários e "aberto agora", fechamento temporário, cobranças, facetas e botões de ação. */
class PlacePageRulesTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    // sexta-feira, 30/10/2026
    static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);

    static Instant at(LocalDate date, String hhmm, ZoneId zone) {
        return date.atTime(java.time.LocalTime.parse(hhmm)).atZone(zone).toInstant();
    }

    static RangeDraft r(String opens, String closes) {
        return new RangeDraft(opens, closes);
    }

    /** Almoço de terça a domingo; jantar sex e sáb até 2h; domingo só almoço; segunda fechado. */
    static HoursDraft bar() {
        Map<String, List<RangeDraft>> w = new LinkedHashMap<>();
        for (String d : List.of("TUESDAY", "WEDNESDAY", "THURSDAY")) {
            w.put(d, List.of(r("11:30", "15:00"), r("18:00", "23:00")));
        }
        w.put("FRIDAY", List.of(r("11:30", "15:00"), r("18:00", "02:00")));
        w.put("SATURDAY", List.of(r("11:30", "15:00"), r("18:00", "02:00")));
        w.put("SUNDAY", List.of(r("11:30", "16:00")));
        return new HoursDraft(w, List.of());
    }

    static OpeningHours hours(HoursDraft d, List<String> errors) {
        return OpeningHours.from(PlaceDetailsRules.hours(d, FRIDAY, errors));
    }

    @Test
    void openNowFollowsTheWeekCrossesMidnightAndSaysWhenItOpensNext() {
        List<String> errors = new ArrayList<>();
        OpeningHours h = hours(bar(), errors);
        assertThat(errors).isEmpty();

        OpeningHours.Status lunch = h.status(at(FRIDAY, "12:00", SP), SP, false, false, null);
        assertThat(lunch.state()).isEqualTo(OpeningHours.State.OPEN);
        assertThat(lunch.label()).isEqualTo("Aberto agora · fecha às 15:00");
        assertThat(h.status(at(FRIDAY, "14:30", SP), SP, false, false, null).label())
                .isEqualTo("Aberto agora · fecha às 15:00 (em 30 min)");
        assertThat(h.status(at(FRIDAY, "16:00", SP), SP, false, false, null).label())
                .isEqualTo("Fechado agora · abre às 18:00");
        // sábado 0h30 ainda é a faixa de sexta (18:00–02:00)
        OpeningHours.Status late = h.status(at(FRIDAY.plusDays(1), "00:30", SP), SP, false, false, null);
        assertThat(late.state()).isEqualTo(OpeningHours.State.OPEN);
        assertThat(late.label()).isEqualTo("Aberto agora · fecha às 02:00");
        assertThat(h.status(at(FRIDAY.plusDays(1), "03:00", SP), SP, false, false, null).label())
                .isEqualTo("Fechado agora · abre às 11:30");
        // domingo à noite: segunda fechado, abre terça
        assertThat(h.status(at(FRIDAY.plusDays(2), "20:00", SP), SP, false, false, null).label())
                .isEqualTo("Fechado agora · abre terça às 11:30");
        assertThat(h.status(at(FRIDAY.plusDays(3), "10:00", SP), SP, false, false, null).label())
                .isEqualTo("Fechado agora · abre amanhã às 11:30");
        assertThat(h.meals()).containsEntry("ALMOCO", true).containsEntry("JANTAR", true)
                .containsEntry("MADRUGADA", true).containsEntry("CAFE_MANHA", false);
    }

    @Test
    void theClockIsTheOneOfThePlaceNotOfTheVisitor() {
        assertThat(PlaceTimeZone.of("SP", "3550308")).isEqualTo(SP);
        assertThat(PlaceTimeZone.of("AM", "1302603")).isEqualTo(ZoneId.of("America/Manaus"));
        assertThat(PlaceTimeZone.of("AC", "1200401")).isEqualTo(ZoneId.of("America/Rio_Branco"));
        assertThat(PlaceTimeZone.of("PE", "2605459")).isEqualTo(ZoneId.of("America/Noronha"));
        assertThat(PlaceTimeZone.of("PE", "2611606")).isEqualTo(ZoneId.of("America/Recife"));
        assertThat(PlaceTimeZone.of(null, null)).isEqualTo(SP);

        OpeningHours h = hours(bar(), new ArrayList<>());
        ZoneId manaus = ZoneId.of("America/Manaus");
        // 15:30 em Brasília = 14:30 em Manaus: aberto lá
        Instant instant = at(FRIDAY, "15:30", SP);
        assertThat(h.status(instant, SP, false, false, null).open()).isFalse();
        assertThat(h.status(instant, manaus, false, false, null).open()).isTrue();
    }

    @Test
    void specialDatesReplaceTheWeekAndLongClosuresAskForTemporarilyClosed() {
        List<String> errors = new ArrayList<>();
        HoursDraft d = new HoursDraft(bar().weekly(), List.of(
                new SpecialDayDraft(FRIDAY.toString(), true, null, "Feriado municipal"),
                new SpecialDayDraft(FRIDAY.minusDays(3).toString(), true, null, "já passou"),
                new SpecialDayDraft(FRIDAY.plusDays(1).toString(), false, List.of(r("10:00", "14:00")), null)));
        PlaceDetails.HoursData data = PlaceDetailsRules.hours(d, FRIDAY, errors);
        assertThat(errors).isEmpty();
        assertThat(data.special()).hasSize(2);   // a data passada sai
        OpeningHours h = OpeningHours.from(data);
        OpeningHours.Status s = h.status(at(FRIDAY, "12:00", SP), SP, false, false, null);
        assertThat(s.state()).isEqualTo(OpeningHours.State.CLOSED);
        assertThat(s.label()).isEqualTo("Fechado agora · abre amanhã às 10:00");
        assertThat(s.note()).isEqualTo("Horário especial hoje (Feriado municipal): fechado");

        List<SpecialDayDraft> eight = new ArrayList<>();
        for (int i = 1; i <= 8; i++) eight.add(new SpecialDayDraft(FRIDAY.plusDays(i).toString(), true, null, null));
        PlaceDetailsRules.hours(new HoursDraft(null, eight), FRIDAY, errors);
        assertThat(errors).anyMatch(e -> e.contains("fechado temporariamente"));
    }

    @Test
    void noWeeklyHoursIsUnknownNeverClosedAndStatusesComeFirst() {
        OpeningHours none = OpeningHours.from(null);
        assertThat(none.status(at(FRIDAY, "12:00", SP), SP, false, false, null).label()).isEqualTo("Horário não informado");
        assertThat(none.status(at(FRIDAY, "12:00", SP), SP, true, false, null).label()).isEqualTo("Fechou de vez");
        assertThat(none.status(at(FRIDAY, "12:00", SP), SP, false, true, FRIDAY.plusDays(20)).label())
                .isEqualTo("Fechado temporariamente · volta em 19/11/2026");
        OpeningHours.Status late = none.status(at(FRIDAY, "12:00", SP), SP, false, true, FRIDAY.minusDays(1));
        assertThat(late.state()).isEqualTo(OpeningHours.State.MAYBE_CLOSED);
        assertThat(late.label()).startsWith("Pode estar fechado");

        OpeningHours always = hours(new HoursDraft(Map.of("MONDAY", List.of(r("00:00", "00:00")),
                "TUESDAY", List.of(r("00:00", "00:00")), "WEDNESDAY", List.of(r("00:00", "00:00")),
                "THURSDAY", List.of(r("00:00", "00:00")), "FRIDAY", List.of(r("00:00", "00:00")),
                "SATURDAY", List.of(r("00:00", "00:00")), "SUNDAY", List.of(r("00:00", "00:00"))), List.of()),
                new ArrayList<>());
        assertThat(always.status(at(FRIDAY, "03:00", SP), SP, false, false, null).label()).isEqualTo("Aberto 24 horas");

        List<String> errors = new ArrayList<>();
        assertThat(PlaceDetailsRules.temporaryClosure(new TemporaryClosureDraft(true, FRIDAY.toString()), FRIDAY, errors)
                .closed()).isTrue();
        assertThat(errors).containsExactly("A data de volta precisa ser depois de hoje.");
        errors.clear();
        PlaceDetailsRules.temporaryClosure(new TemporaryClosureDraft(false, FRIDAY.plusDays(3).toString()), FRIDAY, errors);
        assertThat(errors).containsExactly("Data de volta só com \"fechado temporariamente\".");
    }

    @Test
    void invalidHoursComeBackTogether() {
        List<String> errors = new ArrayList<>();
        PlaceDetailsRules.hours(new HoursDraft(Map.of(
                "MONDAY", List.of(r("11:00", "15:00"), r("14:00", "18:00")),
                "TUESDAY", List.of(r("25:00", "26:00")),
                "WEDNESDAY", List.of(r("10:00", "10:00")),
                "THURSDAY", List.of(r("08:00", "09:00"), r("10:00", "11:00"), r("12:00", "13:00"), r("14:00", "15:00")),
                "SATURDAY", List.of(r("22:00", "06:00")),
                "SUNDAY", List.of(r("05:00", "12:00")),
                "FERIADO", List.of()), List.of()), FRIDAY, errors);
        assertThat(errors).anyMatch(e -> e.equals("Em segunda, as faixas 11:00–15:00 e 14:00–18:00 se sobrepõem."))
                .anyMatch(e -> e.startsWith("Horário inválido em terça"))
                .anyMatch(e -> e.contains("abre e fecha no mesmo horário"))
                .anyMatch(e -> e.startsWith("No máximo 3 faixas"))
                .anyMatch(e -> e.contains("A faixa de sábado que passa da meia-noite (22:00–06:00) invade o horário de domingo"))
                .anyMatch(e -> e.contains("FERIADO"));
    }

    @Test
    void chargesFollowTheConsumerLaw() {
        Map<String, String> facets = Map.of("PIX", "SIM", "MUSICA_AO_VIVO", "SIM");
        List<String> errors = new ArrayList<>();
        PlaceDetails.ChargesData ok = PlaceDetailsRules.charges(new ChargesDraft(10,
                new CouvertDraft("ARTISTICO", 1500, List.of("SATURDAY", "FRIDAY")),
                List.of(new PaymentAdjustmentDraft("PIX", "DESCONTO", 5)), null), facets, errors);
        assertThat(errors).isEmpty();
        assertThat(ok.couvert().days()).containsExactly("FRIDAY", "SATURDAY");

        PlaceDetailsRules.charges(new ChargesDraft(25, new CouvertDraft("ARTISTICO", 1500, List.of()),
                List.of(new PaymentAdjustmentDraft("DINHEIRO", "DESCONTO", 5),
                        new PaymentAdjustmentDraft("BITCOIN", "DESCONTO", 5)), 5000),
                Map.of("DINHEIRO", "NAO", "MUSICA_AO_VIVO", "NAO"), errors);
        assertThat(errors).anyMatch(e -> e.startsWith("Consumação mínima não é aceita"))
                .anyMatch(e -> e.startsWith("Taxa de serviço de 1% a 20%"))
                .anyMatch(e -> e.contains("escolha os dias"))
                .anyMatch(e -> e.contains("\"Música ao vivo\" está como Não"))
                .anyMatch(e -> e.contains("não aceita Dinheiro"))
                .anyMatch(e -> e.contains("BITCOIN"));

        // zero = sem taxa
        assertThat(PlaceDetailsRules.charges(new ChargesDraft(0, null, null, null), Map.of(), new ArrayList<>())
                .serviceFeePercent()).isNull();
    }

    @Test
    void facetsAreDeclaredByTheOwnerExceptTheOnesTheLawFixesOrTheHoursDerive() {
        List<String> errors = new ArrayList<>();
        Map<String, String> ok = PlaceDetailsRules.facets(Map.of("WIFI", "SIM", "PET_FRIENDLY", "SO_AREA_EXTERNA",
                "ENTRADA_ACESSIVEL", "PARCIAL", "PIX", "SIM", "GRUPOS", "DESCONHECIDO", "VALE_REFEICAO", "SIM"),
                EstablishmentGroup.RESTAURANTE, errors);
        assertThat(errors).isEmpty();
        assertThat(ok).containsOnlyKeys("WIFI", "PET_FRIENDLY", "ENTRADA_ACESSIVEL", "PIX", "VALE_REFEICAO");

        PlaceDetailsRules.facets(Map.of("CAO_ASSISTENCIA", "SIM", "ALMOCO", "SIM", "DELIVERY_APP", "SIM",
                "WIFI", "TALVEZ", "VALE_REFEICAO", "SIM", "UNICORNIO", "SIM"), EstablishmentGroup.VAREJO, errors);
        assertThat(errors).anyMatch(e -> e.contains("garantido por lei"))
                .anyMatch(e -> e.contains("vem do horário"))
                .anyMatch(e -> e.contains("vem dos links de delivery"))
                .anyMatch(e -> e.startsWith("Wi-Fi para clientes: use SIM, NAO"))
                .anyMatch(e -> e.contains("varejo aceita vale-alimentação"))
                .anyMatch(e -> e.contains("UNICORNIO"));
        assertThat(Facet.PET_FRIENDLY.valueLabel("NAO")).contains("cão de assistência sempre entra");
    }

    @Test
    void actionButtonsAreOnlyLinksToAllowedDomains() {
        assertThat(PlaceDetailsRules.url("https://www.ifood.com.br/delivery/bh/sabor", ActionProvider.IFOOD))
                .contains("https://www.ifood.com.br/delivery/bh/sabor");
        assertThat(PlaceDetailsRules.url("https://WWW.IFOOD.COM.BR/x", ActionProvider.IFOOD)).contains("https://www.ifood.com.br/x");
        assertThat(PlaceDetailsRules.url("http://www.ifood.com.br/x", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.url("https://ifood.com.br.golpe.com/x", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.url("https://falsoifood.com.br/x", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.url("https://user@ifood.com.br/x", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.url("https://ifood.com.br:8443/x", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.url("javascript:alert(1)", ActionProvider.IFOOD)).isEmpty();
        assertThat(PlaceDetailsRules.whatsapp("(31) 99999-8888")).contains("5531999998888");
        assertThat(PlaceDetailsRules.whatsapp("+55 31 3333-4444")).contains("553133334444");
        assertThat(PlaceDetailsRules.whatsapp("99999-8888")).isEmpty();

        List<String> errors = new ArrayList<>();
        PlaceDetails.ActionsData ok = PlaceDetailsRules.actions(new ActionsDraft(List.of(
                new ActionLinkDraft("IFOOD", "https://www.ifood.com.br/delivery/bh/sabor", true),
                new ActionLinkDraft("RAPPI", "https://www.rappi.com.br/restaurantes/1", false),
                new ActionLinkDraft("THEFORK", "https://www.thefork.com.br/restaurante/sabor", null)),
                "(31) 99999-8888"), errors);
        assertThat(errors).isEmpty();
        assertThat(ok.links()).hasSize(3);
        assertThat(ok.whatsapp()).isEqualTo("5531999998888");

        PlaceDetailsRules.actions(new ActionsDraft(List.of(
                new ActionLinkDraft("IFOOD", "https://golpe.example/ifood", false),
                new ActionLinkDraft("RAPPI", "https://www.rappi.com.br/a", true),
                new ActionLinkDraft("AIQFOME", "https://aiqfome.com/a", true),
                new ActionLinkDraft("THEFORK", "https://www.thefork.com.br/a", true),
                new ActionLinkDraft("WHATSAPP", "https://wa.me/5531999998888", false)), "123"), errors);
        assertThat(errors).anyMatch(e -> e.startsWith("Link do iFood: endereço HTTPS de ifood.com.br"))
                .anyMatch(e -> e.equals("Escolha só um app de delivery preferido."))
                .anyMatch(e -> e.equals("Só um app de delivery pode ser o preferido."))
                .anyMatch(e -> e.contains("WhatsApp vai no campo do número"))
                .anyMatch(e -> e.startsWith("WhatsApp: informe o número com DDD"));
    }
}
