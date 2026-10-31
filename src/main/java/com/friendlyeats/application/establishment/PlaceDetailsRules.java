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
import com.friendlyeats.domain.model.OpeningHours.Range;
import com.friendlyeats.domain.model.PlaceDetails;
import com.friendlyeats.domain.taxonomy.ActionProvider;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.Facet;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Regras da página do lugar (RF11): horários, fechamento temporário, cobranças declaradas, facetas e botões de ação.
 * Puras, como {@link EstablishmentRules}: cada método acrescenta os problemas em {@code errors} (a API devolve
 * todos juntos, 422) e devolve o valor normalizado para gravar.
 */
public final class PlaceDetailsRules {

    static final int MAX_RANGES_PER_DAY = 3;
    static final int MAX_SPECIAL_DAYS = 60;
    static final int MAX_DAYS_AHEAD = 366;
    /** Mais que isso fechado em seguida é "fechado temporariamente", não datas especiais (RC-EST-04). */
    static final int MAX_CLOSED_RUN = 7;
    static final int MAX_NOTE = 60;
    static final int MAX_SERVICE_FEE_PERCENT = 20;
    static final int MAX_COUVERT_CENTS = 50_000;
    static final int MAX_ADJUSTMENT_PERCENT = 20;
    static final int MAX_DELIVERY_LINKS = 5;
    static final int MAX_RESERVATION_LINKS = 2;
    static final int MAX_URL = 500;

    private PlaceDetailsRules() {
    }

    public record Closure(boolean closed, LocalDate reopensOn) {
    }

    // ------------------------------------------------------------ horários

    /**
     * Até 3 faixas por dia, sem sobreposição; a faixa que passa da meia-noite não pode invadir a primeira do dia
     * seguinte. Datas especiais: no máximo 60, até 1 ano à frente, sem repetir; as que já passaram são descartadas, e
     * mais de 7 dias seguidos fechados pede "fechado temporariamente".
     */
    public static PlaceDetails.HoursData hours(HoursDraft d, LocalDate today, List<String> errors) {
        if (d == null) {
            return null;
        }
        Map<String, List<String>> weekly = null;
        if (d.weekly() != null) {
            Map<DayOfWeek, List<Range>> days = new EnumMap<>(DayOfWeek.class);
            d.weekly().forEach((code, ranges) -> {
                Optional<DayOfWeek> day = Arrays.stream(DayOfWeek.values()).filter(x -> x.name().equals(code)).findFirst();
                if (day.isEmpty()) {
                    errors.add("Dia da semana desconhecido: \"" + code + "\" (use MONDAY a SUNDAY).");
                    return;
                }
                days.put(day.get(), ranges(ranges, OpeningHours.dayName(day.get()), errors));
            });
            for (DayOfWeek day : DayOfWeek.values()) {
                List<Range> list = days.getOrDefault(day, List.of());
                List<Range> next = days.getOrDefault(day.plus(1), List.of());
                if (!list.isEmpty() && !next.isEmpty() && list.getLast().overnight()
                        && list.getLast().end() - OpeningHours.DAY_MINUTES > next.getFirst().opens()) {
                    errors.add("A faixa de " + OpeningHours.dayName(day) + " que passa da meia-noite ("
                            + dash(list.getLast()) + ") invade o horário de " + OpeningHours.dayName(day.plus(1)) + " ("
                            + next.getFirst().opensText() + ").");
                }
            }
            weekly = new LinkedHashMap<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                List<Range> list = days.getOrDefault(day, List.of());
                if (!list.isEmpty()) {
                    weekly.put(day.name(), list.stream().map(Range::text).toList());
                }
            }
        }
        List<PlaceDetails.SpecialDayData> special = new ArrayList<>();
        TreeMap<LocalDate, Boolean> closedDates = new TreeMap<>();
        List<SpecialDayDraft> drafts = d.special() == null ? List.of() : d.special();
        if (drafts.size() > MAX_SPECIAL_DAYS) {
            errors.add("No máximo " + MAX_SPECIAL_DAYS + " datas especiais.");
        }
        Set<LocalDate> seen = new LinkedHashSet<>();
        for (SpecialDayDraft s : drafts) {
            LocalDate date = date(s.date(), "Data especial", errors);
            if (date == null || date.isBefore(today)) {
                continue;   // data que já passou não interessa mais
            }
            if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
                errors.add("Datas especiais até 1 ano à frente (" + br(date) + ").");
                continue;
            }
            if (!seen.add(date)) {
                errors.add("Data especial repetida: " + br(date) + ".");
                continue;
            }
            List<RangeDraft> raw = s.ranges() == null ? List.of() : s.ranges();
            boolean closed = Boolean.TRUE.equals(s.closed()) || (s.closed() == null && raw.isEmpty());
            if (closed && !raw.isEmpty()) {
                errors.add("Em " + br(date) + ": marque \"fechado\" ou informe as faixas, não os dois.");
            }
            if (!closed && raw.isEmpty()) {
                errors.add("Em " + br(date) + ": informe as faixas de horário ou marque \"fechado\".");
            }
            List<Range> list = closed ? List.of() : ranges(raw, br(date), errors);
            String note = EstablishmentRules.text(s.note(), "note", MAX_NOTE, errors);
            special.add(new PlaceDetails.SpecialDayData(date.toString(), list.stream().map(Range::text).toList(), note));
            closedDates.put(date, closed);
        }
        int run = 0;
        LocalDate previous = null;
        for (Map.Entry<LocalDate, Boolean> e : closedDates.entrySet()) {
            run = e.getValue() ? (previous != null && previous.plusDays(1).equals(e.getKey()) && run > 0 ? run + 1 : 1) : 0;
            previous = e.getKey();
            if (run == MAX_CLOSED_RUN + 1) {
                errors.add("Para fechar mais de " + MAX_CLOSED_RUN + " dias seguidos, use \"fechado temporariamente\" "
                        + "com a data de volta, em vez de datas especiais.");
            }
        }
        special.sort(Comparator.comparing(PlaceDetails.SpecialDayData::date));
        return new PlaceDetails.HoursData(weekly, special);
    }

    private static List<Range> ranges(List<RangeDraft> drafts, String where, List<String> errors) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        if (drafts.size() > MAX_RANGES_PER_DAY) {
            errors.add("No máximo " + MAX_RANGES_PER_DAY + " faixas de horário por dia (" + where + ").");
        }
        List<Range> out = new ArrayList<>();
        for (RangeDraft r : drafts) {
            Optional<Range> range = r == null ? Optional.empty() : Range.of(r.opens(), r.closes());
            if (range.isEmpty()) {
                errors.add("Horário inválido em " + where + ": use HH:MM, ex.: 11:30.");
            } else if (range.get().opens() == range.get().closes()) {
                errors.add("Em " + where + ", a faixa abre e fecha no mesmo horário (" + range.get().opensText()
                        + "). Para 24 horas, use 00:00 a 00:00.");
            } else {
                out.add(range.get());
            }
        }
        out.sort(Comparator.comparingInt(Range::opens));
        for (int i = 0; i + 1 < out.size(); i++) {
            if (out.get(i).end() > out.get(i + 1).opens()) {
                errors.add("Em " + where + ", as faixas " + dash(out.get(i)) + " e " + dash(out.get(i + 1))
                        + " se sobrepõem.");
            }
        }
        return out;
    }

    /** Pausa: a data de volta é opcional, mas, se vier, fica no futuro e até 1 ano à frente. */
    public static Closure temporaryClosure(TemporaryClosureDraft d, LocalDate today, List<String> errors) {
        if (d == null) {
            return null;
        }
        boolean closed = Boolean.TRUE.equals(d.closed());
        LocalDate reopens = d.reopensOn() == null || d.reopensOn().isBlank() ? null
                : date(d.reopensOn(), "Data de volta", errors);
        if (!closed && reopens != null) {
            errors.add("Data de volta só com \"fechado temporariamente\".");
        }
        if (closed && reopens != null && !reopens.isAfter(today)) {
            errors.add("A data de volta precisa ser depois de hoje.");
        }
        if (closed && reopens != null && reopens.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            errors.add("Data de volta até 1 ano à frente; se não há previsão, deixe em branco.");
        }
        return new Closure(closed, closed ? reopens : null);
    }

    // ------------------------------------------------------------ cobranças

    /**
     * Taxa de serviço de 1 a 20% (0 = sem taxa), sempre exibida como opcional; couvert artístico só com os dias de
     * apresentação ao vivo; desconto ou acréscimo por forma de pagamento (Lei 10.962/2004, art. 5º-A). Consumação
     * mínima é recusada. {@code facets} é o estado final das facetas, para conferir a coerência.
     */
    public static PlaceDetails.ChargesData charges(ChargesDraft d, Map<String, String> facets, List<String> errors) {
        if (d == null) {
            return null;
        }
        if (d.minimumSpendCents() != null) {
            errors.add("Consumação mínima não é aceita: os Procons a consideram cobrança abusiva (CDC art. 39). "
                    + "Cobre só o que for consumido.");
        }
        Integer fee = d.serviceFeePercent();
        if (fee != null && fee == 0) {
            fee = null;
        }
        if (fee != null && (fee < 1 || fee > MAX_SERVICE_FEE_PERCENT)) {
            errors.add("Taxa de serviço de 1% a " + MAX_SERVICE_FEE_PERCENT + "% (ela é sempre opcional para o cliente).");
        }
        PlaceDetails.CouvertData couvert = couvert(d.couvert(), facets, errors);
        List<PlaceDetails.PaymentAdjustmentData> adjustments = new ArrayList<>();
        Set<String> methods = new LinkedHashSet<>();
        for (PaymentAdjustmentDraft a : d.paymentAdjustments() == null ? List.<PaymentAdjustmentDraft>of() : d.paymentAdjustments()) {
            if (a == null) continue;
            Optional<Facet> method = Facet.fromCode(a.method()).filter(f -> f.getSection() == Facet.Section.PAGAMENTO);
            if (method.isEmpty()) {
                errors.add("Forma de pagamento desconhecida: \"" + a.method() + "\".");
                continue;
            }
            if (!methods.add(method.get().name())) {
                errors.add("Uma condição por forma de pagamento (" + method.get().getLabel() + " repetido).");
            }
            if (!"DESCONTO".equals(a.kind()) && !"ACRESCIMO".equals(a.kind())) {
                errors.add(method.get().getLabel() + ": diga se é DESCONTO ou ACRESCIMO.");
            }
            if (a.percent() == null || a.percent() < 1 || a.percent() > MAX_ADJUSTMENT_PERCENT) {
                errors.add(method.get().getLabel() + ": percentual de 1% a " + MAX_ADJUSTMENT_PERCENT + "%.");
            }
            if ("NAO".equals(facets.get(method.get().name()))) {
                errors.add("Condição para " + method.get().getLabel() + ", mas a página diz que o lugar não aceita "
                        + method.get().getLabel() + ".");
            }
            adjustments.add(new PlaceDetails.PaymentAdjustmentData(method.get().name(), a.kind(), a.percent()));
        }
        return new PlaceDetails.ChargesData(fee, couvert, adjustments);
    }

    /** O que está gravado, de volta em pedido, para revalidar contra facetas novas. */
    static ChargesDraft draftOf(PlaceDetails.ChargesData c) {
        return new ChargesDraft(c.serviceFeePercent(), c.couvert() == null ? null
                : new CouvertDraft(c.couvert().kind(), c.couvert().cents(), c.couvert().days()),
                c.paymentAdjustments() == null ? List.of() : c.paymentAdjustments().stream()
                        .map(a -> new PaymentAdjustmentDraft(a.method(), a.kind(), a.percent())).toList(), null);
    }

    private static PlaceDetails.CouvertData couvert(CouvertDraft c, Map<String, String> facets, List<String> errors) {
        if (c == null || (c.kind() == null && c.cents() == null)) {
            return null;
        }
        if (!"ARTISTICO".equals(c.kind()) && !"ENTRADA_MESA".equals(c.kind())) {
            errors.add("Couvert: tipo ARTISTICO (apresentação ao vivo) ou ENTRADA_MESA (pão, petiscos).");
            return null;
        }
        if (c.cents() == null || c.cents() < 1 || c.cents() > MAX_COUVERT_CENTS) {
            errors.add("Couvert: valor por pessoa de R$ 0,01 a R$ 500.");
        }
        List<String> days = new ArrayList<>();
        if ("ARTISTICO".equals(c.kind())) {
            Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
            for (String code : c.days() == null ? List.<String>of() : c.days()) {
                Optional<DayOfWeek> day = Arrays.stream(DayOfWeek.values()).filter(x -> x.name().equals(code)).findFirst();
                if (day.isEmpty()) {
                    errors.add("Dia da semana desconhecido no couvert: \"" + code + "\".");
                } else {
                    set.add(day.get());
                }
            }
            if (set.isEmpty() && (c.days() == null || c.days().isEmpty())) {
                errors.add("Couvert artístico só nos dias com apresentação ao vivo: escolha os dias.");
            }
            if ("NAO".equals(facets.get(Facet.MUSICA_AO_VIVO.name()))) {
                errors.add("Couvert artístico exige apresentação ao vivo, mas \"Música ao vivo\" está como Não.");
            }
            set.forEach(day -> days.add(day.name()));
        }
        return new PlaceDetails.CouvertData(c.kind(), c.cents(), days);
    }

    // ------------------------------------------------------------ facetas

    /**
     * Só as facetas que o dono declara: as fixas por lei e as derivadas (refeições pelo horário, delivery por app
     * pelos links) são recusadas. Valor vazio ou DESCONHECIDO = "Sem informação" (sai do mapa).
     */
    public static Map<String, String> facets(Map<String, String> d, EstablishmentGroup group, List<String> errors) {
        Map<String, String> out = new LinkedHashMap<>();
        if (d == null) {
            return out;
        }
        new TreeMap<>(d).forEach((code, value) -> {
            if (value == null || value.isBlank() || "DESCONHECIDO".equals(value)) {
                return;
            }
            Optional<Facet> facet = Facet.fromCode(code);
            if (facet.isEmpty()) {
                errors.add("Faceta desconhecida: \"" + code + "\" (veja GET /api/v1/taxonomy).");
                return;
            }
            Facet f = facet.get();
            if (f.fixed()) {
                errors.add("\"" + f.getLabel() + "\" é garantido por lei e não se edita.");
            } else if (f.getValues() == Facet.Values.DERIVED) {
                errors.add("\"" + f.getLabel() + "\" vem " + (f.getSection() == Facet.Section.REFEICOES
                        ? "do horário de funcionamento." : "dos links de delivery."));
            } else if (!f.getValues().getCodes().contains(value)) {
                errors.add(f.getLabel() + ": use " + String.join(", ", f.getValues().getCodes()) + ".");
            } else if (group != null && !f.appliesTo(group)) {
                errors.add(f.getLabel() + " não se aplica a este tipo de lugar"
                        + (f == Facet.VALE_REFEICAO ? " (varejo aceita vale-alimentação)." : " (só varejo e polos).")
                        );
            } else {
                out.put(f.name(), value);
            }
        });
        return out;
    }

    // ------------------------------------------------------------ botões de ação

    /**
     * Um link por provedor, HTTPS e no domínio do provedor; até 2 de reserva e 5 de delivery, com no máximo um delivery
     * preferido. WhatsApp pelo número com DDD, guardado em E.164 (sem o +).
     */
    public static PlaceDetails.ActionsData actions(ActionsDraft d, List<String> errors) {
        if (d == null) {
            return null;
        }
        List<PlaceDetails.ActionLinkData> links = new ArrayList<>();
        Set<ActionProvider> seen = EnumSet.noneOf(ActionProvider.class);
        int delivery = 0;
        int reservation = 0;
        int preferred = 0;
        for (ActionLinkDraft l : d.links() == null ? List.<ActionLinkDraft>of() : d.links()) {
            if (l == null) continue;
            Optional<ActionProvider> provider = ActionProvider.fromCode(l.provider());
            if (provider.isEmpty()) {
                errors.add("Provedor desconhecido: \"" + l.provider() + "\" (veja GET /api/v1/taxonomy).");
                continue;
            }
            ActionProvider p = provider.get();
            if (p.getKind() == ActionProvider.Kind.WHATSAPP) {
                errors.add("O WhatsApp vai no campo do número, não como link.");
                continue;
            }
            if (!seen.add(p)) {
                errors.add("Um link por provedor (" + p.getLabel() + " repetido).");
                continue;
            }
            Optional<String> url = url(l.url(), p);
            if (url.isEmpty()) {
                errors.add("Link do " + p.getLabel() + ": endereço HTTPS de " + String.join(" ou ", p.getDomains())
                        + " (ex.: https://www." + p.getDomains().getFirst() + "/...).");
                continue;
            }
            boolean pref = Boolean.TRUE.equals(l.preferred());
            if (pref && p.getKind() != ActionProvider.Kind.DELIVERY) {
                errors.add("Só um app de delivery pode ser o preferido.");
                pref = false;
            }
            if (p.getKind() == ActionProvider.Kind.DELIVERY) delivery++;
            if (p.getKind() == ActionProvider.Kind.RESERVA) reservation++;
            if (pref) preferred++;
            links.add(new PlaceDetails.ActionLinkData(p.name(), url.get(), pref));
        }
        if (delivery > MAX_DELIVERY_LINKS) {
            errors.add("No máximo " + MAX_DELIVERY_LINKS + " apps de delivery.");
        }
        if (reservation > MAX_RESERVATION_LINKS) {
            errors.add("No máximo " + MAX_RESERVATION_LINKS + " links de reserva.");
        }
        if (preferred > 1) {
            errors.add("Escolha só um app de delivery preferido.");
        }
        String whatsapp = null;
        if (d.whatsapp() != null && !d.whatsapp().isBlank()) {
            Optional<String> number = whatsapp(d.whatsapp());
            if (number.isEmpty()) {
                errors.add("WhatsApp: informe o número com DDD, ex.: (31) 99999-8888.");
            } else {
                whatsapp = number.get();
            }
        }
        return links.isEmpty() && whatsapp == null ? null : new PlaceDetails.ActionsData(links, whatsapp);
    }

    /** HTTPS, sem usuário nem porta estranha, no domínio do provedor (ou subdomínio). Devolve o link normalizado. */
    static Optional<String> url(String raw, ActionProvider p) {
        if (raw == null || raw.isBlank() || raw.strip().length() > MAX_URL) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw.strip());
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !p.acceptsHost(host)) {
                return Optional.empty();
            }
            return Optional.of(new URI("https", null, host, -1, uri.getPath(), uri.getQuery(), uri.getFragment())
                    .toASCIIString());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** Número brasileiro com DDD (8 ou 9 dígitos), aceitando +55, espaços, traços e parênteses → "5531999998888". */
    static Optional<String> whatsapp(String raw) {
        String digits = raw.replaceAll("[\\s().+-]", "");
        if (!digits.matches("^\\d+$")) {
            return Optional.empty();
        }
        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }
        return digits.matches("^55[1-9][1-9]\\d{8,9}$") ? Optional.of(digits) : Optional.empty();
    }

    // ------------------------------------------------------------ apoio

    private static LocalDate date(String text, String what, List<String> errors) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException | NullPointerException e) {
            errors.add(what + " inválida: use AAAA-MM-DD.");
            return null;
        }
    }

    private static String dash(Range r) {
        return r.opensText() + "–" + r.closesText();
    }

    static String br(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
