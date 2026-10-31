package com.friendlyeats.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Horário de funcionamento (RF11): faixas por dia da semana, datas especiais que substituem a semana e o cálculo do
 * "aberto agora" no fuso do lugar ({@link PlaceTimeZone}).
 *
 * <ul>
 *   <li>Faixa em minutos do dia: abre de 0 a 1439, fecha de 1 a 1440 (1440 = meia-noite, "00:00" ao digitar).</li>
 *   <li>Faixa que fecha antes (ou na hora) em que abre passa da meia-noite e pertence ao dia em que começa
 *       (sexta 18:00–02:00 é a madrugada de sábado).</li>
 *   <li>Sem horário da semana e sem data especial para hoje, o estado é "Horário não informado", nunca "Fechado".</li>
 * </ul>
 */
public final class OpeningHours {

    public static final int DAY_MINUTES = 1440;
    private static final Pattern RANGE = Pattern.compile("^(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})$");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Até quantos minutos antes de fechar o rótulo avisa "em N min". */
    static final int CLOSING_SOON_MINUTES = 60;

    /** Faixa de horário em minutos do dia. */
    public record Range(int opens, int closes) {

        public boolean overnight() {
            return closes <= opens;
        }

        /** Fim em minutos a partir do início do dia em que a faixa começa (passa de 1440 na madrugada). */
        public int end() {
            return overnight() ? closes + DAY_MINUTES : closes;
        }

        public String opensText() {
            return hhmm(opens);
        }

        public String closesText() {
            return hhmm(closes % DAY_MINUTES);
        }

        /** "11:30-15:00", o formato gravado. */
        public String text() {
            return opensText() + "-" + closesText();
        }

        /** "HH:MM" e "HH:MM" (fecha em "00:00" ou "24:00" = meia-noite). Vazio se algum horário for inválido. */
        public static Optional<Range> of(String opens, String closes) {
            Optional<Integer> o = minutes(opens);
            Optional<Integer> c = minutes(closes);
            if (o.isEmpty() || c.isEmpty() || o.get() >= DAY_MINUTES) {
                return Optional.empty();
            }
            int close = c.get() == 0 ? DAY_MINUTES : c.get();
            return Optional.of(new Range(o.get(), close));
        }

        /** Lê o formato gravado "HH:MM-HH:MM". */
        public static Optional<Range> parse(String text) {
            Matcher m = text == null ? null : RANGE.matcher(text);
            if (m == null || !m.matches()) {
                return Optional.empty();
            }
            return of(m.group(1) + ":" + m.group(2), m.group(3) + ":" + m.group(4));
        }
    }

    public record SpecialDay(LocalDate date, List<Range> ranges, String note) {
        public boolean closed() {
            return ranges.isEmpty();
        }
    }

    public enum State { OPEN, CLOSED, UNKNOWN, TEMPORARILY_CLOSED, MAYBE_CLOSED, PERMANENTLY_CLOSED }

    /**
     * Situação em um instante, com o texto pronto ("Aberto agora · fecha às 23:00"). {@code closesAt} e
     * {@code opensAt} são na hora local do lugar; {@code note} traz o horário especial de hoje, se houver.
     */
    public record Status(State state, String label, LocalDateTime closesAt, LocalDateTime opensAt, String note) {
        public boolean open() {
            return state == State.OPEN;
        }
    }

    private final Map<DayOfWeek, List<Range>> weekly;
    private final Map<LocalDate, SpecialDay> special;

    /** {@code weekly} nulo = horário da semana não informado. */
    public OpeningHours(Map<DayOfWeek, List<Range>> weekly, List<SpecialDay> special) {
        this.weekly = weekly == null ? null : new EnumMap<>(weekly);
        Map<LocalDate, SpecialDay> s = new LinkedHashMap<>();
        special.stream().sorted(Comparator.comparing(SpecialDay::date)).forEach(d -> s.put(d.date(), d));
        this.special = s;
    }

    /** Do JSON gravado; faixas ilegíveis são ignoradas (a gravação já passou pela validação). */
    public static OpeningHours from(PlaceDetails.HoursData data) {
        if (data == null) {
            return new OpeningHours(null, List.of());
        }
        Map<DayOfWeek, List<Range>> weekly = null;
        if (data.weekly() != null) {
            weekly = new EnumMap<>(DayOfWeek.class);
            for (Map.Entry<String, List<String>> e : data.weekly().entrySet()) {
                weekly.put(DayOfWeek.valueOf(e.getKey()), parseAll(e.getValue()));
            }
        }
        List<SpecialDay> special = new ArrayList<>();
        if (data.special() != null) {
            for (PlaceDetails.SpecialDayData d : data.special()) {
                special.add(new SpecialDay(LocalDate.parse(d.date()), parseAll(d.ranges()), d.note()));
            }
        }
        return new OpeningHours(weekly, special);
    }

    private static List<Range> parseAll(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        return texts.stream().map(Range::parse).flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(Range::opens)).toList();
    }

    public boolean weeklyInformed() {
        return weekly != null;
    }

    /** Faixas do dia da semana (vazio = fechado; só faz sentido com {@link #weeklyInformed()}). */
    public List<Range> weekly(DayOfWeek day) {
        return weekly == null ? List.of() : weekly.getOrDefault(day, List.of());
    }

    public List<SpecialDay> special() {
        return List.copyOf(special.values());
    }

    /** Datas especiais de hoje em diante, em ordem. */
    public List<SpecialDay> upcoming(LocalDate today) {
        return special.values().stream().filter(d -> !d.date().isBefore(today)).toList();
    }

    /** Faixas de uma data: a especial, se houver; senão, a da semana; {@code null} = não informado. */
    List<Range> rangesOn(LocalDate date) {
        SpecialDay s = special.get(date);
        if (s != null) {
            return s.ranges();
        }
        return weekly == null ? null : weekly.getOrDefault(date.getDayOfWeek(), List.of());
    }

    private List<LocalDateTime[]> intervals(LocalDate date) {
        List<Range> ranges = rangesOn(date);
        if (ranges == null) {
            return null;
        }
        LocalDateTime start = date.atStartOfDay();
        return ranges.stream().map(r -> new LocalDateTime[]{start.plusMinutes(r.opens()), start.plusMinutes(r.end())})
                .toList();
    }

    // ------------------------------------------------------------ "aberto agora"

    /**
     * Situação no instante {@code now}, no fuso {@code zone}. A ordem: fechou de vez, fechado temporariamente (com a
     * data de volta; passada a data sem o dono confirmar, "pode estar fechado"), e então o horário.
     */
    public Status status(java.time.Instant now, ZoneId zone, boolean permanentlyClosed, boolean temporarilyClosed,
                         LocalDate reopensOn) {
        LocalDateTime local = ZonedDateTime.ofInstant(now, zone).toLocalDateTime();
        LocalDate today = local.toLocalDate();
        if (permanentlyClosed) {
            return new Status(State.PERMANENTLY_CLOSED, "Fechou de vez", null, null, null);
        }
        if (temporarilyClosed) {
            if (reopensOn == null) {
                return new Status(State.TEMPORARILY_CLOSED, "Fechado temporariamente", null, null, null);
            }
            if (today.isBefore(reopensOn)) {
                return new Status(State.TEMPORARILY_CLOSED,
                        "Fechado temporariamente · volta em " + reopensOn.format(FULL_DATE), null, null, null);
            }
            return new Status(State.MAYBE_CLOSED, "Pode estar fechado: a volta estava prevista para "
                    + reopensOn.format(FULL_DATE) + " e o estabelecimento ainda não confirmou", null, null, null);
        }
        String note = todayNote(today);
        // aberto agora: faixas de ontem (madrugada) e de hoje
        for (LocalDate d : List.of(today.minusDays(1), today)) {
            List<LocalDateTime[]> list = intervals(d);
            if (list == null) {
                continue;
            }
            for (LocalDateTime[] iv : list) {
                if (!local.isBefore(iv[0]) && local.isBefore(iv[1])) {
                    LocalDateTime closes = extend(iv[1]);
                    return new Status(State.OPEN, openLabel(local, closes), closes, null, note);
                }
            }
        }
        if (rangesOn(today) == null && rangesOn(today.minusDays(1)) == null) {
            return new Status(State.UNKNOWN, "Horário não informado", null, null, note);
        }
        LocalDateTime next = nextOpening(local);
        if (next == null) {
            return new Status(State.CLOSED, "Fechado agora · sem horário nos próximos 7 dias", null, null, note);
        }
        return new Status(State.CLOSED, "Fechado agora · abre " + when(local, next), null, next, note);
    }

    private String todayNote(LocalDate today) {
        SpecialDay s = special.get(today);
        if (s == null) {
            return null;
        }
        String what = s.closed() ? "fechado" : String.join(" e ", s.ranges().stream()
                .map(r -> r.opensText() + "–" + r.closesText()).toList());
        return "Horário especial hoje" + (s.note() == null ? "" : " (" + s.note() + ")") + ": " + what;
    }

    /** Junta faixas encostadas (sexta 18:00–24:00 + sábado 00:00–02:00) para dizer quando fecha de fato. */
    private LocalDateTime extend(LocalDateTime end) {
        LocalDateTime current = end;
        for (int guard = 0; guard < 8; guard++) {
            LocalDateTime probe = current;
            Optional<LocalDateTime> more = java.util.stream.Stream.of(probe.toLocalDate().minusDays(1), probe.toLocalDate())
                    .map(this::intervals).filter(java.util.Objects::nonNull).flatMap(List::stream)
                    .filter(iv -> iv[0].equals(probe) && iv[1].isAfter(probe)).map(iv -> iv[1]).findFirst();
            if (more.isEmpty()) {
                return current;
            }
            current = more.get();
        }
        return current;
    }

    private LocalDateTime nextOpening(LocalDateTime local) {
        for (int offset = 0; offset <= 7; offset++) {
            List<LocalDateTime[]> list = intervals(local.toLocalDate().plusDays(offset));
            if (list == null) {
                continue;
            }
            Optional<LocalDateTime> first = list.stream().map(iv -> iv[0]).filter(t -> t.isAfter(local)).min(Comparator.naturalOrder());
            if (first.isPresent()) {
                return first.get();
            }
        }
        return null;
    }

    private static String openLabel(LocalDateTime now, LocalDateTime closes) {
        if (Duration.between(now, closes).toDays() >= 7) {
            return "Aberto 24 horas";
        }
        String at = closes.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? "à meia-noite"
                : "às " + hhmm(closes.getHour() * 60 + closes.getMinute());
        long minutes = Duration.between(now, closes).toMinutes();
        String soon = minutes <= CLOSING_SOON_MINUTES ? " (em " + Math.max(1, minutes) + " min)" : "";
        return "Aberto agora · fecha " + at + soon;
    }

    /** "às 18:00", "amanhã às 11:30", "sexta às 11:30" ou "em 25/12 às 11:00". */
    private static String when(LocalDateTime now, LocalDateTime next) {
        String at = "às " + hhmm(next.getHour() * 60 + next.getMinute());
        long days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate());
        if (days == 0) return at;
        if (days == 1) return "amanhã " + at;
        if (days < 7) return dayName(next.getDayOfWeek()) + " " + at;
        return "em " + next.toLocalDate().format(DAY_MONTH) + " " + at;
    }

    // ------------------------------------------------------------ refeições derivadas (taxonomia 8.2)

    /** Café da manhã (abre antes das 10h), almoço (aberto entre 11h e 15h), jantar (depois das 19h), madrugada. */
    public Map<String, Boolean> meals() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (weekly == null) {
            return out;
        }
        List<Range> all = weekly.values().stream().flatMap(List::stream).toList();
        out.put("CAFE_MANHA", all.stream().anyMatch(r -> r.opens() < 10 * 60));
        out.put("ALMOCO", all.stream().anyMatch(r -> r.opens() < 15 * 60 && r.end() > 11 * 60));
        out.put("JANTAR", all.stream().anyMatch(r -> r.end() > 19 * 60));
        out.put("MADRUGADA", all.stream().anyMatch(r -> r.overnight() && r.closes() > 0));
        return out;
    }

    // ------------------------------------------------------------ apoio

    public static String dayName(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }

    static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** "H:MM" ou "HH:MM" → minutos; "24:00" vale 1440 (meia-noite no fim do dia). */
    static Optional<Integer> minutes(String text) {
        Matcher m = text == null ? null : TIME.matcher(text.strip());
        if (m == null || !m.matches()) {
            return Optional.empty();
        }
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h == 24 && min == 0) {
            return Optional.of(DAY_MINUTES);
        }
        if (h > 23 || min > 59) {
            return Optional.empty();
        }
        return Optional.of(h * 60 + min);
    }
}
