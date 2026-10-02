package br.com.reservas.dashboard.application;

/** Arquivo pronto de `GET /exports/{type}` (F8-2): bytes + nome + `Content-Type`. */
public record ExportFile(byte[] content, String filename, String contentType) {
}
