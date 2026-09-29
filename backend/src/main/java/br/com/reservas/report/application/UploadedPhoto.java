package br.com.reservas.report.application;

/**
 * Bytes crus de um arquivo enviado, antes da validação de tipo/tamanho
 * (RN-17). Cópia local de `area.application.UploadedPhoto` (mesmo formato,
 * módulos diferentes não compartilham tipo de aplicação — CLAUDE.md §5).
 */
public record UploadedPhoto(byte[] content) {
}
