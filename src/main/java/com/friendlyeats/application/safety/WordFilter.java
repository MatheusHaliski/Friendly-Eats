package com.friendlyeats.application.safety;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Palavras filtradas (RF19 · RC-SEG-09). Compara sem acento, sem maiúsculas, com as trocas comuns de letras por
 * números e símbolos ("1d1ota", "burr@") e com letras repetidas encolhidas ("idiotaaaa"). A comparação é por palavra
 * inteira ou expressão, para "lixo" não pegar "lixeira".
 */
public final class WordFilter {

    public static final int MAX_WORDS = 100;
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 40;

    /**
     * Lista padrão contra intimidação, ligada para menor de 16 (ECA Digital, art. 6º, §2º). ⚖️ A curadoria final da
     * lista fica com a equipe de confiança e segurança.
     */
    public static final List<String> DEFAULT_WORDS = List.of(
            "idiota", "imbecil", "burro", "burra", "otario", "otaria", "retardado", "retardada", "lixo humano",
            "nojento", "nojenta", "baleia", "vagabundo", "vagabunda", "se mata", "vai morrer",
            "ninguem gosta de voce", "aberracao");

    private WordFilter() {
    }

    /** Forma comparável de um texto: minúsculas, sem acento, números e símbolos trocados, só letras e espaços. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            out.append(switch (ch) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> Character.isLetterOrDigit(ch) ? ch : ' ';
            });
        }
        // letras repetidas 3+ vezes viram uma ("idiotaaaa" -> "idiota")
        return out.toString().replaceAll("(\\p{L})\\1{2,}", "$1").replaceAll("\\s+", " ").strip();
    }

    /** Palavra válida para a lista (já normalizada), ou vazio. */
    public static Optional<String> clean(String word) {
        String w = normalize(word);
        return w.length() < MIN_LENGTH || w.length() > MAX_LENGTH ? Optional.empty() : Optional.of(w);
    }

    /** O texto contém alguma das palavras (normalizadas), como palavra inteira ou expressão. */
    public static boolean matches(String text, Collection<String> normalizedWords) {
        if (normalizedWords.isEmpty() || text == null) {
            return false;
        }
        String padded = " " + normalize(text) + " ";
        for (String w : normalizedWords) {
            if (!w.isEmpty() && padded.contains(" " + w + " ")) {
                return true;
            }
        }
        return false;
    }
}
