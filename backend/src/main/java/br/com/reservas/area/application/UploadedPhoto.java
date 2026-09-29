package br.com.reservas.area.application;

/**
 * Bytes crus de um arquivo enviado, antes da validacao de tipo/tamanho
 * (RN-17). O nome original do arquivo nunca e usado para nada (nem para
 * decidir extensao): a chave no storage e sempre `{uuid}.{ext}` com a
 * extensao detectada pelos bytes (vibe-security: nunca confiar em nome ou
 * `Content-Type` vindos do cliente).
 */
public record UploadedPhoto(byte[] content) {
}
