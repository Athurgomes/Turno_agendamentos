package br.com.reservas.unit.domain;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * RN-02: usuario/identificador da unidade = normalizacao de bloco+numero,
 * minusculo, sem espacos ou acentos: {@code {bloco}-{numero}} ou
 * {@code {numero}} quando nao ha bloco. Unico por condominio (garantido pela
 * unicidade parcial de {@code unit.identifier}, V3); nao editavel pelo
 * morador (so exposto por {@code UnitDetail.username}).
 */
public final class UnitIdentifier {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    private UnitIdentifier() {
    }

    public static String normalize(String block, String number) {
        String normalizedNumber = strip(number);
        String normalizedBlock = strip(block);
        return normalizedBlock.isEmpty() ? normalizedNumber : normalizedBlock + "-" + normalizedNumber;
    }

    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
            .replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutDiacritics.toLowerCase()).replaceAll("");
    }
}
