package com.friendlyeats.application.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RF19 · palavras filtradas: acentos, l33t, letras repetidas e palavra inteira. */
class WordFilterTest {

    @Test
    void normalizesAccentsLeetAndRepeatedLetters() {
        assertThat(WordFilter.normalize("ÓTÁRIO!!")).isEqualTo("otario");
        assertThat(WordFilter.normalize("1d10t@aaa")).isEqualTo("idiota");
        assertThat(WordFilter.normalize("  Lixo   Humano ")).isEqualTo("lixo humano");
    }

    @Test
    void matchesWholeWordsAndPhrasesOnly() {
        List<String> words = List.of("lixo", "se mata");
        assertThat(WordFilter.matches("Você é um LIXO.", words)).isTrue();
        assertThat(WordFilter.matches("Joga na lixeira", words)).isFalse();
        assertThat(WordFilter.matches("vai, se mata", words)).isTrue();
        assertThat(WordFilter.matches("ele se matava de rir", words)).isFalse();
        assertThat(WordFilter.matches("qualquer coisa", List.of())).isFalse();
    }

    @Test
    void cleansWordsForTheList() {
        assertThat(WordFilter.clean(" Idiota ")).contains("idiota");
        assertThat(WordFilter.clean("x")).isEmpty();
        assertThat(WordFilter.clean("a".repeat(41))).isEmpty();
        assertThat(WordFilter.DEFAULT_WORDS).allSatisfy(w -> assertThat(WordFilter.normalize(w)).isEqualTo(w));
    }
}
