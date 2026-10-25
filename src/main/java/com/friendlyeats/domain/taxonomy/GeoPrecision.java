package com.friendlyeats.domain.taxonomy;

/** De onde vem a coordenada do estabelecimento; a busca por raio (RF10) rebaixa o que for menos preciso que rua. */
public enum GeoPrecision {
    GEO_PIN_CONFIRMED, GEO_ROOFTOP, GEO_STREET, GEO_CEP_CENTROID, GEO_BAIRRO_CENTROID, GEO_MUNICIPIO_CENTROID
}
