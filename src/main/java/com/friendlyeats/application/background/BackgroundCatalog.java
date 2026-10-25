package com.friendlyeats.application.background;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo procedural da moldura do post (RF16): tudo é arte do próprio Friendly Eats em CSS/SVG, sem banco de
 * imagens de terceiros e sem URL ou CSS vindo do cliente. O post guarda só ids; a versão muda quando o catálogo muda.
 */
@Component
public class BackgroundCatalog {

    public record Item(String id, String name, String css, List<String> palette) {
    }

    /** Padrão com "clima" de mesa: toalha xadrez, azulejo, brasa, feira. */
    public record Aura(String id, String name, String mood, List<String> palette, String technique, String css) {
    }

    public record SeasonView(String id, String name, String window, int priority, List<String> colors, boolean auto) {
    }

    public record Layout(String id, String name, boolean ownArt) {
    }

    public record Skin(String id, String name, String containerColor) {
    }

    public record CatalogView(String version, String activeSeasonId, List<Item> colors, List<Item> gradients,
                              List<SeasonView> seasons, List<Item> animations, List<Aura> auras,
                              List<Item> materials, List<Layout> layouts, List<Skin> skins,
                              List<Item> containerColors, Rules rules) {
    }

    /** Regras que o cliente precisa saber para não mandar algo que o servidor recusa. */
    public record Rules(double minContrast, int maxConfigBytes, int maxAnimationSeconds, List<String> feedsWithoutFrame) {
    }

    static final List<Item> COLORS = List.of(
            color("#2A2119", "Carvão"), color("#3B1F2B", "Berinjela"), color("#1E2A38", "Noite"),
            color("#24382E", "Manjericão"), color("#7A2E1F", "Páprica"), color("#E88F12", "Laranja"),
            color("#E9C46A", "Mel"), color("#F5EDE3", "Farinha"));

    static final List<Item> GRADIENTS = List.of(
            new Item("brasa", "Brasa", "linear-gradient(135deg,#FFB703,#E85D04 55%,#370617)", List.of("#FFB703", "#E85D04", "#370617")),
            new Item("acai", "Açaí", "linear-gradient(135deg,#7B2CBF,#240046)", List.of("#7B2CBF", "#240046")),
            new Item("matcha", "Matcha", "linear-gradient(135deg,#B5E48C,#2D6A4F)", List.of("#B5E48C", "#2D6A4F")),
            new Item("caramelo", "Caramelo", "linear-gradient(135deg,#E9C46A,#8D5524)", List.of("#E9C46A", "#8D5524")),
            new Item("pimenta", "Pimenta", "linear-gradient(135deg,#FF4D2E,#7A0C02)", List.of("#FF4D2E", "#7A0C02")),
            new Item("cafe", "Café", "linear-gradient(135deg,#8B5A2B,#1B110A)", List.of("#8B5A2B", "#1B110A")));

    /** Animação leve da arte: toca uma vez, no máximo 5 s, e para com "Reduzir movimento". */
    static final List<Item> ANIMATIONS = List.of(
            new Item("NONE", "Sem animação", null, List.of()),
            new Item("FUMEGAR", "Fumegar", "vapor sobe e some, 2 × 2,4 s", List.of()),
            new Item("DERIVA", "Deriva", "background-position 0 → 100 %, 4,8 s", List.of()));

    static final List<Aura> AURAS = List.of(
            new Aura("toalha-xadrez", "Toalha xadrez", "piquenique · cantina", List.of("#C1121F", "#F5EDE3"), "xadrez",
                    "repeating-linear-gradient(0deg,#C1121F80 0 12px,#0000 12px 24px),repeating-linear-gradient(90deg,#C1121F80 0 12px,#0000 12px 24px),#F5EDE3"),
            new Aura("azulejo", "Azulejo", "boteco · cozinha da vó", List.of("#E76F51", "#2A9D8F", "#F4A261", "#F1FAEE"), "azulejo",
                    "conic-gradient(from 45deg,#E76F51 0 25%,#F4A261 0 50%,#2A9D8F 0 75%,#F1FAEE 0) 0 0/18px 18px"),
            new Aura("forno", "Forno a lenha", "pizzaria · padaria", List.of("#FFB703", "#E85D04", "#9D0208", "#3A0E0A"), "mesh quente",
                    "radial-gradient(70% 60% at 50% 100%,#E85D04cc,#0000 70%),radial-gradient(50% 40% at 30% 80%,#FFB703aa,#0000 70%),radial-gradient(60% 50% at 70% 70%,#9D0208aa,#0000 75%),#2A0F0A"),
            new Aura("mare", "Maré", "frutos do mar · praia", List.of("#90E0EF", "#00B4D8", "#0077B6", "#F5EDE3"), "ondas",
                    "radial-gradient(120% 60% at 50% 120%,#0077B6,#0000 60%),repeating-linear-gradient(170deg,#90E0EF2e 0 9px,#0000 9px 22px),linear-gradient(180deg,#00B4D866,#0B2233)"),
            new Aura("favo", "Favo de mel", "doceria · café da manhã", List.of("#E9C46A", "#B08D57", "#2B2118", "#F5EDE3"), "metal escovado",
                    "repeating-linear-gradient(90deg,#ffffff12 0 1px,#0000001a 1px 3px),conic-gradient(from 20deg,#B08D57,#E9C46A 12%,#7A5C2E 25%,#F3DFA2 40%,#6B4F2A 55%,#E9C46A 72%,#8C6A3A 88%,#B08D57)"),
            new Aura("feira", "Feira", "feira livre · hortifrúti", List.of("#E63946", "#F7C948", "#90BE6D", "#F4A261", "#7B2CBF"),
                    "frutas em SVG geradas com a seed do post", null),
            new Aura("horta", "Horta", "vegetal · natural", List.of("#A7C957", "#6A994E", "#F2E8CF", "#386641"), "blobs desfocados",
                    "radial-gradient(45% 45% at 25% 30%,#A7C957aa,#0000 70%),radial-gradient(40% 45% at 75% 70%,#6A994Eaa,#0000 70%),radial-gradient(50% 40% at 70% 20%,#F2E8CF55,#0000 70%),#22311F"),
            new Aura("neon", "Neon de boteco", "bar · noite", List.of("#14110E", "#FF2E88", "#3DDC6B", "#F7C948"), "meio-tom",
                    "radial-gradient(#FF2E8877 1.2px,#0000 1.7px) 0 0/7px 7px,radial-gradient(#3DDC6B55 1px,#0000 1.6px) 3.5px 3.5px/7px 7px,#14110E"));

    static final List<Item> MATERIALS = List.of(
            new Item("papel-manteiga", "Papel manteiga", null, List.of()), new Item("linho", "Linho", null, List.of()),
            new Item("inox", "Inox", null, List.of()), new Item("vidro", "Vidro", null, List.of()));

    static final List<String> VARIANTS = List.of("ORIGINAL", "SUAVE");
    static final List<String> FORMATS = List.of("IMAGEM_UNICA", "MOSAICO");
    static final List<Layout> LAYOUTS = List.of(new Layout("CLASSICO", "Clássico", false),
            new Layout("POLAROID", "Polaroid", false), new Layout("CARDAPIO", "Cardápio (lousa)", true));
    static final List<Skin> SKINS = List.of(new Skin("PADRAO", "Padrão", "#2A2119"),
            new Skin("MONO", "Mono", "#191714"), new Skin("LEVE", "Leve", "#33281E"));
    static final List<Item> CONTAINER_COLORS = List.of(color("#2A2119", "Carvão"), color("#1E2A38", "Noite"),
            color("#F5EDE3", "Farinha"), color("#E88F12", "Laranja"));
    static final Rules RULES = new Rules(Contrast.AA_TEXT, 2048, 5, List.of("ALERT"));

    private final String version;

    public BackgroundCatalog() {
        this.version = digest(String.join("|", COLORS.toString(), GRADIENTS.toString(), seasons().toString(),
                ANIMATIONS.toString(), AURAS.toString(), MATERIALS.toString(), LAYOUTS.toString(), SKINS.toString(),
                CONTAINER_COLORS.toString(), RULES.toString()));
    }

    public CatalogView view(LocalDate today) {
        return new CatalogView(version, SeasonalCalendar.activeOn(today).map(SeasonalCalendar.Season::id).orElse(null),
                COLORS, GRADIENTS, seasons(), ANIMATIONS, AURAS, MATERIALS, LAYOUTS, SKINS, CONTAINER_COLORS, RULES);
    }

    public String version() {
        return version;
    }

    // ------------------------------------------------------------ consultas usadas na validação e na leitura

    boolean hasColor(String hex) {
        return COLORS.stream().anyMatch(c -> c.id().equalsIgnoreCase(hex));
    }

    boolean hasGradient(String id) {
        return GRADIENTS.stream().anyMatch(g -> g.id().equals(id));
    }

    boolean hasAnimation(String id) {
        return ANIMATIONS.stream().anyMatch(a -> a.id().equals(id));
    }

    boolean hasAura(String id) {
        return AURAS.stream().anyMatch(a -> a.id().equals(id));
    }

    boolean hasMaterial(String id) {
        return MATERIALS.stream().anyMatch(m -> m.id().equals(id));
    }

    Map<String, Layout> layoutsById() {
        return LAYOUTS.stream().collect(Collectors.toMap(Layout::id, Function.identity()));
    }

    Map<String, Skin> skinsById() {
        return SKINS.stream().collect(Collectors.toMap(Skin::id, Function.identity()));
    }

    private static List<SeasonView> seasons() {
        return SeasonalCalendar.SEASONS.stream()
                .map(s -> new SeasonView(s.id(), s.name(), s.window(), s.priority(), s.colors(), s.auto()))
                .toList();
    }

    private static Item color(String hex, String name) {
        return new Item(hex, name, hex, List.of(hex));
    }

    private static String digest(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
