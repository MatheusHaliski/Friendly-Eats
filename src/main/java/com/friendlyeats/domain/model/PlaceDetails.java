package com.friendlyeats.domain.model;

import java.util.List;
import java.util.Map;

/**
 * O que o dono declara na página do lugar (RF11), como fica gravado nas colunas JSON da tabela {@code establishment}
 * (V6). Só texto, número e listas: o Jackson grava e lê sem módulo extra. As regras estão em
 * {@code PlaceDetailsRules}; o cálculo do "aberto agora", em {@link OpeningHours}.
 */
public final class PlaceDetails {

    private PlaceDetails() {
    }

    /**
     * {@code weekly}: dia (MONDAY…SUNDAY) → faixas "HH:MM-HH:MM"; dia ausente = fechado. {@code weekly} nulo = horário
     * não informado. {@code special}: datas que substituem o horário da semana.
     */
    public record HoursData(Map<String, List<String>> weekly, List<SpecialDayData> special) {
    }

    /** {@code ranges} vazio = fechado na data. */
    public record SpecialDayData(String date, List<String> ranges, String note) {
    }

    public record ChargesData(Integer serviceFeePercent, CouvertData couvert, List<PaymentAdjustmentData> paymentAdjustments) {
    }

    /** {@code kind}: ARTISTICO (com apresentação ao vivo nos dias) ou ENTRADA_MESA (pão, petiscos). */
    public record CouvertData(String kind, Integer cents, List<String> days) {
    }

    /** {@code method}: código da faceta de pagamento; {@code kind}: DESCONTO ou ACRESCIMO. */
    public record PaymentAdjustmentData(String method, String kind, Integer percent) {
    }

    /** {@code whatsapp}: só dígitos, com o 55 (E.164 sem o +). */
    public record ActionsData(List<ActionLinkData> links, String whatsapp) {
    }

    public record ActionLinkData(String provider, String url, boolean preferred) {
    }
}
