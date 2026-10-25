package com.friendlyeats.domain.model;

import java.util.regex.Pattern;

/**
 * Validação de CPF (11 dígitos, os dois últimos verificadores no módulo 11), usada no cadastro de negócios informais
 * (ambulante, food truck, cozinha caseira) que não têm CNPJ. O CPF é dado pessoal: fica só nas chaves privadas de
 * {@code type_details} e nunca aparece no perfil público. Existência e titularidade são conferidas pela moderação.
 */
public final class Cpf {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{11}$");

    private Cpf() {
    }

    /** Remove pontuação (". -") e espaços. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("[.\\-\\s]", "");
    }

    public static boolean isValid(String raw) {
        String cpf = normalize(raw);
        if (cpf == null || !FORMAT.matcher(cpf).matches() || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return cpf.charAt(9) - '0' == digit(cpf, 9) && cpf.charAt(10) - '0' == digit(cpf, 10);
    }

    private static int digit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
