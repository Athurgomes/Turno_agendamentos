package br.com.reservas.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-17: deteccao do tipo real do arquivo pelos bytes iniciais. */
class ImageFileValidatorTest {

    @Test
    @DisplayName("RN-17: JPEG valido (FF D8 FF) e reconhecido como image/jpeg")
    void detectsJpeg() {
        var result = ImageFileValidator.validate(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
    }

    @Test
    @DisplayName("RN-17: PNG valido e reconhecido como image/png")
    void detectsPng() {
        var result = ImageFileValidator.validate(
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("RN-17: WEBP valido (RIFF....WEBP) e reconhecido como image/webp")
    void detectsWebp() {
        byte[] content = "RIFFxxxxWEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var result = ImageFileValidator.validate(content);
        assertThat(result.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("RN-17: conteudo que nao bate com nenhum dos 3 formatos -> INVALID_FILE, mesmo com extensao de imagem")
    void rejectsContentThatIsNotARealImage() {
        assertThatThrownBy(() -> ImageFileValidator.validate("%PDF-1.4".getBytes()))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-17: arquivo maior que 5 MB e rejeitado mesmo com cabecalho valido")
    void rejectsFileAboveMaxSize() {
        byte[] content = new byte[(int) ImageFileValidator.MAX_SIZE_BYTES + 1];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        assertThatThrownBy(() -> ImageFileValidator.validate(content)).isInstanceOf(BusinessException.class);
    }
}
