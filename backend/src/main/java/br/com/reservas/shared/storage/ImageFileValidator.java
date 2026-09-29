package br.com.reservas.shared.storage;

import br.com.reservas.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * RN-17: valida o tipo real de uma imagem pelos bytes iniciais (nunca pela
 * extensão do nome nem pelo {@code Content-Type} enviado pelo cliente, que são
 * controlados pelo atacante) e o tamanho máximo. Usado por qualquer módulo que
 * aceite upload de foto (área agora; reports na F6).
 */
public final class ImageFileValidator {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC =
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageFileValidator() {
    }

    public record Validated(String contentType, String extension) {
    }

    /** Detecta JPEG/PNG/WEBP pelos bytes iniciais; senão ou acima de 5 MB, `422 INVALID_FILE`. */
    public static Validated validate(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_SIZE_BYTES) {
            throw invalidFile();
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return new Validated("image/jpeg", "jpg");
        }
        if (startsWith(content, PNG_MAGIC)) {
            return new Validated("image/png", "png");
        }
        if (isWebp(content)) {
            return new Validated("image/webp", "webp");
        }
        throw invalidFile();
    }

    private static boolean isWebp(byte[] content) {
        if (content.length < 12) {
            return false;
        }
        String riff = new String(content, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(content, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp);
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException invalidFile() {
        return new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
            "Arquivo inválido: envie JPG, PNG ou WEBP de até 5 MB.");
    }
}
