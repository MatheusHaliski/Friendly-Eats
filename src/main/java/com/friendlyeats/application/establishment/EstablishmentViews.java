package com.friendlyeats.application.establishment;

import com.friendlyeats.application.account.AccountViews.CategoryView;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.PriceLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Respostas da API de estabelecimentos (RF6, RF7), avaliações (RF8) e da página do lugar (RF11). Todo código vem com
 * o rótulo pt-BR.
 */
public final class EstablishmentViews {

    private EstablishmentViews() {
    }

    /** Código estável + rótulo; {@code group} é a macro da culinária, a família do formato ou o grupo do tipo. */
    public record Coded(String code, String label, String group) {
    }

    public record ChipView(String code, String label, String lucideIcon, String darkHex, String lightHex) {
        static ChipView of(CuisineChip c) {
            return c == null ? null : new ChipView(c.name(), c.getLabel(), c.getLucideIcon(), c.getDarkHex(), c.getLightHex());
        }
    }

    public record DietaryOfferView(String code, String label, String level) {
    }

    public record ClassificationView(Coded primaryCuisine, List<Coded> secondaryCuisines, Coded primaryFormat,
                                     List<Coded> secondaryFormats, Coded approach, List<DietaryOfferView> dietaryOffers,
                                     ChipView chip) {
    }

    /**
     * Faixa exibida: com 5+ relatos de gasto nos últimos 12 meses vale a mediana deles; senão, o declarado pelo dono.
     * O texto acessível evita que o leitor de tela diga "dólar dólar".
     */
    public record PriceView(PriceLevel level, String glyph, String label, String rangeLabel, PriceLevel.Source source,
                            int reportCount, Integer declaredMinCents, Integer declaredMaxCents, String accessibleLabel) {
    }

    /** Endereço como o público pode ver; o dono vê tudo. */
    public record LocationView(String country, String uf, String municipalityIbge, String city, String neighborhood,
                               String street, String streetNumber, String complement, String cep, Double latitude,
                               Double longitude, String geoPrecision, String addressVisibility, boolean approximate) {
    }

    public record RatingView(String state, BigDecimal average, String averageLabel, int count,
                             Map<Integer, Integer> histogram, Map<String, BigDecimal> dimensions,
                             String accessibleLabel) {
    }

    public record OwnerView(long id, String name, String handle, CategoryView category, boolean verified) {
    }

    /**
     * Página do estabelecimento. {@code published} e {@code publishBlockers} só vêm para o dono: a página fica
     * fora do ar enquanto o selo da conta não for conferido (RF1) ou a conta não estiver ativa.
     * {@code viewerCanEdit}: quem pede pode editar (hoje só o dono; a equipe do RF20 entra pelo {@link PlaceEditors}).
     */
    public record EstablishmentView(long id, String name, String description, Coded primaryType,
                                    List<Coded> secondaryTypes, Coded group, ClassificationView classification,
                                    PriceView price, LocationView location, RatingView rating, OwnerView owner,
                                    boolean adultsOnly, Establishment.Status status, Boolean published,
                                    List<String> publishBlockers, Instant createdAt, Instant updatedAt,
                                    OpenStatusView openStatus, HoursView hours, ChargesView charges,
                                    List<FacetSectionView> facets, List<ActionView> actions, boolean viewerCanEdit) {
    }

    // ------------------------------------------------------------ página do lugar (RF11)

    /**
     * "Aberto agora" no fuso do lugar. {@code state}: OPEN, CLOSED, UNKNOWN, TEMPORARILY_CLOSED, MAYBE_CLOSED ou
     * PERMANENTLY_CLOSED; {@code closesAt}/{@code opensAt} na hora local (AAAA-MM-DDTHH:MM); {@code note} = horário
     * especial de hoje.
     */
    public record OpenStatusView(String state, boolean open, String label, String closesAt, String opensAt,
                                 String note, String timeZone) {
    }

    /** {@code closes} "00:00" = meia-noite; {@code overnight} = passa da meia-noite. */
    public record RangeView(String opens, String closes, boolean overnight) {
    }

    public record DayHoursView(String day, String label, List<RangeView> ranges, boolean closed, boolean today) {
    }

    public record SpecialDayView(String date, String label, boolean closed, List<RangeView> ranges, String note) {
    }

    public record TemporaryClosureView(boolean closed, String reopensOn) {
    }

    /** {@code informed} = há horário da semana; sem ele, a semana vem vazia e a página diz "Horário não informado". */
    public record HoursView(String timeZone, boolean informed, List<DayHoursView> weekly, List<SpecialDayView> special,
                            TemporaryClosureView temporaryClosure) {
    }

    /** Uma linha de cobrança, com o texto fixo exibido ao público ("Taxa de serviço de 10% (opcional)"). */
    public record ChargeLine(String code, String label, String text, String detail) {
    }

    public record CouvertView(String kind, String label, int cents, List<Coded> days) {
    }

    public record PaymentAdjustmentView(String method, String methodLabel, String kind, int percent, String text) {
    }

    /**
     * Cobranças declaradas: os valores (para o formulário do dono) e as linhas prontas. {@code declaredLabel}:
     * "Declarado pelo estabelecimento em 30/10/2026"; {@code notice}: a página não substitui o aviso no local.
     */
    public record ChargesView(Integer serviceFeePercent, CouvertView couvert,
                              List<PaymentAdjustmentView> paymentAdjustments, List<ChargeLine> lines,
                              Instant declaredAt, String declaredLabel, String notice) {
    }

    /** {@code origin}: DECLARADO_DONO, LEI, HORARIO ou LINKS. */
    public record FacetView(String code, String label, String value, String valueLabel, String lucideIcon,
                            String origin, String originLabel) {
    }

    public record FacetSectionView(String code, String label, List<FacetView> items) {
    }

    /**
     * Botão de ação: {@code kind} RESERVA, DELIVERY ou WHATSAPP; {@code exitNotice} é o aviso de saída ("Você vai sair
     * do Friendly Eats…"); {@code phone} só no WhatsApp (+55…).
     */
    public record ActionView(String kind, String provider, String providerLabel, String label, String url,
                             boolean preferred, String exitNotice, String phone) {
    }

    public record ReviewAuthorView(String name, String handle, CategoryView category) {
    }

    public record ReviewView(long id, int rating, String ratingLabel, Coded visitMode, Map<String, Integer> dimensions,
                             Integer spentPerPersonCents, String body, String trust, boolean edited,
                             ReviewAuthorView author, Instant createdAt, Instant updatedAt) {
    }

    public record ReviewPage(List<ReviewView> items, int page, int size, long total, RatingView rating) {
    }
}
