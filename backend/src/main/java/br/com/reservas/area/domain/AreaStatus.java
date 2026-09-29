package br.com.reservas.area.domain;

/** `AreaStatus` (docs/02, RN-14): so `ACTIVE` aceita novas reservas. */
public enum AreaStatus {
    ACTIVE("Disponível"),
    MAINTENANCE("Em manutenção"),
    RENOVATION("Em reforma"),
    INTERDICTED("Interditada"),
    INACTIVE("Desativada");

    private final String label;

    AreaStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
