package com.friendlyeats.application.background;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Cartelas da época para a moldura do post (RF16), no Hemisfério Sul e com a cozinha de cada uma: frutas do verão,
 * abóbora do outono, caldos e pinhão do inverno, flores comestíveis da primavera, chocolate da Páscoa, quentão das
 * festas juninas, a ceia de Natal. Janelas semiabertas {@code [início, fim)} avaliadas na data de São Paulo;
 * prioridade 1 vence 4 (data pontual > festa móvel > festa > estação). Campanha comercial (Black Friday) nunca entra
 * no modo automático: só por escolha da pessoa.
 */
public final class SeasonalCalendar {

    public record Season(String id, String name, String window, int priority, List<String> colors, boolean auto,
                         Predicate<LocalDate> active) {
    }

    public static final List<Season> SEASONS = List.of(
            fixed("verao", "Verão (manga, melancia, maracujá)", "21/12–20/03", 4, "12-21", "03-20",
                    "#FFB703", "#FB8500", "#E63946", "#90BE6D", "#F4E1C1"),
            fixed("outono", "Outono (abóbora, caqui)", "20/03–21/06", 4, "03-20", "06-21",
                    "#C8553D", "#E09F3E", "#9E2A2B", "#7A6C5D", "#F2D0A4"),
            fixed("inverno", "Inverno (caldos, pinhão, vinho)", "21/06–22/09", 4, "06-21", "09-22",
                    "#5E2A2B", "#8C6E5D", "#C9D6E3", "#2E4A62", "#E8DCC8"),
            fixed("primavera", "Primavera (morango, flores comestíveis)", "22/09–21/12", 4, "09-22", "12-21",
                    "#F28AB2", "#E63946", "#90BE6D", "#F6C90E", "#FFF0F3"),
            new Season("carnaval", "Carnaval (comida de bloco)", "sexta a Quarta de Cinzas (Páscoa − 51 a − 46)", 2,
                    List.of("#FF2E88", "#FFD23F", "#00C2A8", "#7B2FF7", "#E88F12"), true, SeasonalCalendar::isCarnaval),
            new Season("pascoa", "Páscoa (chocolate)", "Domingo de Ramos ao Domingo de Páscoa", 2,
                    List.of("#5C3317", "#8B5A2B", "#D2A679", "#F3E5AB", "#F28AB2"), true, SeasonalCalendar::isPascoa),
            fixed("junina", "Festas Juninas e Julinas (quentão, pamonha)", "01/06–31/07", 3, "06-01", "08-01",
                    "#E63946", "#F4A261", "#2A9D8F", "#F1C453", "#8D5524"),
            fixed("namorados", "Dia dos Namorados (jantar a dois)", "05/06–12/06", 1, "06-05", "06-13",
                    "#E5383B", "#FF758F", "#FFB3C1", "#9D0208", "#FFF0F3"),
            fixed("dia-da-pizza", "Dia da Pizza", "10/07", 1, "07-10", "07-11",
                    "#C1121F", "#F4F1DE", "#2D6A4F", "#F2CC8F", "#3D2B1F"),
            new Season("black-friday", "Black Friday (só por escolha)", "dia seguinte à 4ª quinta de novembro", 1,
                    List.of("#2B2B2B", "#F7C948", "#E88F12", "#8A8F96", "#F5EDE3"), false, SeasonalCalendar::isBlackFriday),
            fixed("natal", "Ceia de Natal e Réveillon", "01/12–06/01", 3, "12-01", "01-07",
                    "#C1121F", "#2D6A4F", "#E9C46A", "#F5EDE3", "#7A2E1F"));

    private SeasonalCalendar() {
    }

    /** Cartela do modo "automática pela data": a de maior prioridade entre as ativas. */
    public static Optional<Season> activeOn(LocalDate date) {
        return SEASONS.stream()
                .filter(Season::auto)
                .filter(s -> s.active().test(date))
                .min(Comparator.comparingInt(Season::priority));
    }

    public static Optional<Season> byId(String id) {
        return SEASONS.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** Domingo de Páscoa no calendário gregoriano (algoritmo de Meeus/Jones/Butcher). */
    public static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25;
        int g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int n = h + l - 7 * m + 114;
        return LocalDate.of(year, n / 31, n % 31 + 1);
    }

    /** Terça de Carnaval = Páscoa − 47; a janela vai da sexta (− 51) até a Quarta de Cinzas (− 46). */
    static boolean isCarnaval(LocalDate date) {
        LocalDate easter = easter(date.getYear());
        return !date.isBefore(easter.minusDays(51)) && date.isBefore(easter.minusDays(45));
    }

    /** Semana Santa: do Domingo de Ramos (Páscoa − 7) até o Domingo de Páscoa. */
    static boolean isPascoa(LocalDate date) {
        LocalDate easter = easter(date.getYear());
        return !date.isBefore(easter.minusDays(7)) && !date.isAfter(easter);
    }

    static boolean isBlackFriday(LocalDate date) {
        LocalDate thanksgiving = LocalDate.of(date.getYear(), 11, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
        return date.equals(thanksgiving.plusDays(1));
    }

    private static Season fixed(String id, String name, String window, int priority, String from, String to,
                                String... colors) {
        MonthDay start = MonthDay.parse("--" + from);
        MonthDay end = MonthDay.parse("--" + to);
        Predicate<LocalDate> active = date -> {
            MonthDay md = MonthDay.from(date);
            return start.isBefore(end)
                    ? !md.isBefore(start) && md.isBefore(end)
                    : !md.isBefore(start) || md.isBefore(end);          // janela que vira o ano (verão, Natal)
        };
        return new Season(id, name, window, priority, List.of(colors), true, active);
    }
}
