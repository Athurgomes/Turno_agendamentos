package br.com.reservas.area.domain;

/** `InspectionCondition` (docs/02): estado geral registrado numa vistoria (RF-ARE-07). */
public enum InspectionCondition {
    GOOD("Bom"),
    FAIR("Regular"),
    POOR("Ruim");

    private final String label;

    InspectionCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
