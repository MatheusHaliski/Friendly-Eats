package com.friendlyeats.domain.taxonomy;

/**
 * O que o público vê do endereço (RF6). Cozinha caseira com CPF fica sempre em {@link #ADDR_PUBLIC_BAIRRO}: o endereço
 * é a casa do dono (dado pessoal), então a página mostra bairro e município e um ponto arredondado (~550 m).
 */
public enum AddressVisibility {
    ADDR_PUBLIC_FULL, ADDR_PUBLIC_BAIRRO, ADDR_HIDDEN
}
