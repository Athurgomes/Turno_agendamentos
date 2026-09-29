package br.com.reservas.unit.domain;

import br.com.reservas.shared.error.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * RN-07: CPF do morador (obrigatório e com DV válido para o principal;
 * opcional para adicionais). RNF-01: CPF completo só para ADMIN; mascarado
 * (`***.456.789-**`) para os demais perfis, nunca logado.
 */
public final class Cpf {

    private Cpf() {
    }

    /** Remove pontuação e valida os dígitos verificadores; devolve só os 11 dígitos. */
    public static String validate(String rawCpf) {
        String digits = rawCpf == null ? "" : rawCpf.replaceAll("\\D", "");
        if (!isValid(digits)) {
            throw new BusinessException("CPF_INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "CPF inválido.");
        }
        return digits;
    }

    private static boolean isValid(String digits) {
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return digits.charAt(9) - '0' == checkDigit(digits, 9)
            && digits.charAt(10) - '0' == checkDigit(digits, 10);
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        int weight = length + 1;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    /** RNF-01: máscara exibida a SYNDIC/UNIT, ex. `***.456.789-**`. */
    public static String mask(String digits) {
        if (digits == null || digits.length() != 11) {
            return null;
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }
}
