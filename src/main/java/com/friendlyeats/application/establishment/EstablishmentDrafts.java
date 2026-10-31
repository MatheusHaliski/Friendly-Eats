package com.friendlyeats.application.establishment;

import java.util.List;
import java.util.Map;

/**
 * Corpos de pedido da API de estabelecimentos e avaliações (RF6, RF7, RF8) e da página do lugar (RF11), com os
 * códigos da taxonomia em texto.
 */
public final class EstablishmentDrafts {

    private EstablishmentDrafts() {
    }

    /**
     * POST cria com tudo; no PATCH, campo nulo = não mexe. Tipos, local, preço e cada bloco do RF11 (horários,
     * fechamento temporário, cobranças, facetas e botões) trocam por inteiro.
     */
    public record EstablishmentDraft(String name, String description, String primaryType, List<String> secondaryTypes,
                                     LocationDraft location, PriceDraft price, ClassificationDraft classification,
                                     String status, HoursDraft hours, TemporaryClosureDraft temporaryClosure,
                                     ChargesDraft charges, Map<String, String> facets, ActionsDraft actions) {
    }

    // ------------------------------------------------------------ página do lugar (RF11)

    /** Horário "HH:MM"; fechar em "00:00" (ou "24:00") é meia-noite; fechar antes de abrir passa da meia-noite. */
    public record RangeDraft(String opens, String closes) {
    }

    /**
     * {@code weekly}: MONDAY…SUNDAY → até 3 faixas; dia ausente ou lista vazia = fechado; {@code weekly} nulo =
     * "Horário não informado". {@code special}: até 60 datas que substituem a semana.
     */
    public record HoursDraft(Map<String, List<RangeDraft>> weekly, List<SpecialDayDraft> special) {
    }

    /** Data AAAA-MM-DD; {@code closed} = fechado o dia todo (sem faixas). */
    public record SpecialDayDraft(String date, Boolean closed, List<RangeDraft> ranges, String note) {
    }

    /** Pausa com data de volta opcional (AAAA-MM-DD, no futuro). Fechar de vez é {@code status: CLOSED}. */
    public record TemporaryClosureDraft(Boolean closed, String reopensOn) {
    }

    /**
     * Cobranças declaradas. Taxa de serviço de 1 a 20% (sempre opcional ao cliente); couvert; desconto ou acréscimo por
     * forma de pagamento. {@code minimumSpendCents} existe só para ser recusado: consumação mínima não é aceita.
     */
    public record ChargesDraft(Integer serviceFeePercent, CouvertDraft couvert,
                               List<PaymentAdjustmentDraft> paymentAdjustments, Integer minimumSpendCents) {
    }

    /** {@code kind}: ARTISTICO (exige dias com apresentação ao vivo) ou ENTRADA_MESA; valor por pessoa em centavos. */
    public record CouvertDraft(String kind, Integer cents, List<String> days) {
    }

    /** {@code method}: PIX, DINHEIRO, CARTAO_DEBITO…; {@code kind}: DESCONTO ou ACRESCIMO; 1 a 20%. */
    public record PaymentAdjustmentDraft(String method, String kind, Integer percent) {
    }

    /** Links de reserva e delivery (um por provedor) e o número de WhatsApp (com DDD; o +55 é opcional). */
    public record ActionsDraft(List<ActionLinkDraft> links, String whatsapp) {
    }

    public record ActionLinkDraft(String provider, String url, Boolean preferred) {
    }

    /** Município pelo código IBGE de 7 dígitos; o ponto (lat/lng) é o pino confirmado pelo dono no mapa. */
    public record LocationDraft(String uf, String municipalityIbge, String city, String neighborhood, String street,
                                String streetNumber, String complement, String cep, Double latitude, Double longitude,
                                String addressVisibility) {
    }

    /** Gasto típico por pessoa (prato + bebida não alcoólica), em centavos de real. */
    public record PriceDraft(Integer minCents, Integer maxCents, Boolean notApplicable) {
    }

    public record ClassificationDraft(String primaryCuisine, List<String> secondaryCuisines, String primaryFormat,
                                      List<String> secondaryFormats, String approach, Map<String, String> dietaryOffers) {
    }

    /**
     * Nota de 1 a 5 obrigatória; dimensões, gasto por pessoa e texto opcionais. {@code disclosure} (RF13): vínculo
     * declarado com o lugar ({@code NONE}, {@code COURTESY}, {@code INVITATION} ou {@code RELATIONSHIP}); ausente
     * mantém o que estava.
     */
    public record ReviewDraft(Integer rating, String visitMode, Map<String, Integer> dimensions,
                              Integer spentPerPersonCents, String body, String disclosure) {

        public ReviewDraft(Integer rating, String visitMode, Map<String, Integer> dimensions,
                           Integer spentPerPersonCents, String body) {
            this(rating, visitMode, dimensions, spentPerPersonCents, body, null);
        }
    }
}
