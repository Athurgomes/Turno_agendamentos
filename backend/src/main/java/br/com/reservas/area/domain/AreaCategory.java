package br.com.reservas.area.domain;

/** `AreaCategory` (docs/02, RN-13): categorias pre-definidas de area comum. */
public enum AreaCategory {
    PARTY_ROOM("Salão de festas"),
    BARBECUE("Churrasqueira"),
    GOURMET_SPACE("Espaço gourmet"),
    POOL("Piscina"),
    SPORTS_COURT("Quadra poliesportiva"),
    TENNIS_COURT("Quadra de tênis"),
    GYM("Academia"),
    GAME_ROOM("Salão de jogos"),
    TOY_ROOM("Brinquedoteca"),
    PLAYGROUND("Playground"),
    SAUNA("Sauna"),
    CINEMA("Cinema/Home theater"),
    COWORKING("Coworking/Sala de estudos"),
    PET_PLACE("Pet place"),
    OTHER("Outro");

    private final String label;

    AreaCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
