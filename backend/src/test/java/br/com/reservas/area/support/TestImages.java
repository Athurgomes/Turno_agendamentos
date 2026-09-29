package br.com.reservas.area.support;

import java.nio.charset.StandardCharsets;

/** Bytes minimos validos (so os "magic bytes", RN-17) para os testes de upload de `area`. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    public static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};
    }

    public static byte[] webp() {
        byte[] content = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);
        return content;
    }

    public static byte[] fakePdfWithJpgExtension() {
        return "%PDF-1.4 conteudo falso".getBytes(StandardCharsets.US_ASCII);
    }

    /** Cabecalho PNG valido, mas maior que os 5 MB permitidos (RN-17). */
    public static byte[] oversized() {
        byte[] content = new byte[(int) (5 * 1024 * 1024 + 1)];
        byte[] header = png();
        System.arraycopy(header, 0, content, 0, header.length);
        return content;
    }
}
