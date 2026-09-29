package br.com.reservas.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-02: usuario/identificador normalizado da unidade. */
class UnitIdentifierTest {

    @Test
    @DisplayName("RN-02: bloco A, unidade 1203 vira a-1203")
    void blockAndNumber() {
        assertThat(UnitIdentifier.normalize("A", "1203")).isEqualTo("a-1203");
    }

    @Test
    @DisplayName("RN-02: torre T2, unidade 45 vira t2-45")
    void towerAndNumber() {
        assertThat(UnitIdentifier.normalize("T2", "45")).isEqualTo("t2-45");
    }

    @Test
    @DisplayName("RN-02: sem bloco, unidade 101 vira 101")
    void numberOnly() {
        assertThat(UnitIdentifier.normalize(null, "101")).isEqualTo("101");
        assertThat(UnitIdentifier.normalize("  ", "101")).isEqualTo("101");
    }

    @Test
    @DisplayName("RN-02: acentos e espacos sao removidos e tudo vira minusculo")
    void stripsAccentsSpacesAndLowercases() {
        assertThat(UnitIdentifier.normalize(" Á ", "  102 ")).isEqualTo("a-102");
    }
}
