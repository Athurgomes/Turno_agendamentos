package br.com.reservas.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-03: senha temporaria de 6 caracteres, sem ambiguidade visual, gerada com SecureRandom. */
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    @DisplayName("RN-03: gera 6 caracteres, todos do alfabeto sem 0 O 1 I L")
    void generatesSixCharactersFromTheAllowedAlphabet() {
        String password = generator.generate();

        assertThat(password).hasSize(6);
        assertThat(password.chars().allMatch(c -> TemporaryPasswordGenerator.ALPHABET.indexOf(c) >= 0)).isTrue();
        assertThat(TemporaryPasswordGenerator.ALPHABET).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    @DisplayName("RN-03: geracoes sucessivas nao repetem sempre o mesmo valor (aleatoriedade basica)")
    void successiveGenerationsAreNotAllIdentical() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 50).forEach(i -> generated.add(generator.generate()));

        assertThat(generated.size()).isGreaterThan(1);
    }
}
