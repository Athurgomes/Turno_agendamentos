package br.com.reservas.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-07: CPF do morador principal, dígitos verificadores validados. */
class CpfTest {

    @Test
    @DisplayName("RN-07: CPF com DV valido (com mascara/pontuacao) e aceito e normalizado so com digitos")
    void validCpfWithPunctuationIsAccepted() {
        assertThat(Cpf.validate("111.444.777-35")).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("RN-07: CPF com DV invalido -> CPF_INVALID (422)")
    void invalidCheckDigitIsRejected() {
        assertThatThrownBy(() -> Cpf.validate("11144477736"))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("CPF_INVALID"));
    }

    @Test
    @DisplayName("RN-07: CPF com todos os digitos iguais -> CPF_INVALID")
    void repeatedDigitsIsRejected() {
        assertThatThrownBy(() -> Cpf.validate("11111111111"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("RNF-01: mascara mantem so o bloco do meio visivel (***.456.789-**)")
    void maskHidesFirstAndLastBlocks() {
        assertThat(Cpf.mask("11145678999")).isEqualTo("***.456.789-**");
    }
}
