package com.friendlyeats.application.establishment;

import java.util.List;
import java.util.Map;

/** Corpos de pedido da API de estabelecimentos e avaliações (RF6, RF7, RF8), com os códigos da taxonomia em texto. */
public final class EstablishmentDrafts {

    private EstablishmentDrafts() {
    }

    /** POST cria com tudo; no PATCH, campo nulo = não mexe (tipos, local e preço trocam por inteiro). */
    public record EstablishmentDraft(String name, String description, String primaryType, List<String> secondaryTypes,
                                     LocationDraft location, PriceDraft price, ClassificationDraft classification,
                                     String status) {
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
