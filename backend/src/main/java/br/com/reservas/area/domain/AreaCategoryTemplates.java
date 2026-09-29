package br.com.reservas.area.domain;

import java.util.Map;

/**
 * RF-ARE-03/docs/01 secao 6: templates editaveis de regras e sugestoes de
 * conduta por categoria, usados para pre-preencher o formulario de cadastro.
 * O texto comum (base legal, aviso final, conduta geral) fica centralizado
 * aqui para nao repetir o mesmo paragrafo 15 vezes; so o trecho especifico da
 * categoria muda.
 */
public final class AreaCategoryTemplates {

    private static final String LEGAL_BASIS = "Base legal: Código Civil, art. 1.335 (direito do condômino de usar "
        + "as partes comuns conforme sua destinação, sem excluir a utilização dos demais); art. 1.336, IV (dever de "
        + "não usar a propriedade de forma prejudicial ao sossego, à salubridade e à segurança dos demais, ou aos "
        + "bons costumes); art. 1.337 (possibilidade de multa ao condômino que descumpre reiteradamente seus "
        + "deveres ou tem comportamento antissocial); Lei do silêncio municipal e ABNT NBR 10151 (limites de "
        + "ruído, especialmente no período noturno); lotação conforme a capacidade definida e o AVCB (Corpo de "
        + "Bombeiros) do condomínio.";

    private static final String NOTICE = "Estas orientações complementam a Convenção e o Regimento Interno do "
        + "condomínio, que prevalecem em caso de divergência.";

    private static final String GENERAL_CONDUCT = "Respeitar o horário reservado e a capacidade máxima; o "
        + "responsável pela reserva responde pelos convidados; deixar o espaço limpo e organizado ao final; "
        + "comunicar danos encontrados pelo canal de report; respeitar o horário de silêncio; não obstruir saídas "
        + "de emergência.";

    private record Content(String rules, String conduct) {
    }

    private static final Map<AreaCategory, Content> SPECIFIC = Map.ofEntries(
        Map.entry(AreaCategory.PARTY_ROOM, new Content(
            "Uso reservado para festas e eventos sociais da unidade responsável, dentro do horário de "
                + "funcionamento e da capacidade máxima cadastrada.",
            "Manter volume de som moderado; não furar paredes nem usar fita adesiva que danifique a pintura; "
                + "entregar o espaço até o horário final da reserva.")),
        Map.entry(AreaCategory.BARBECUE, new Content(
            "Uso reservado para preparo de churrasco e confraternização, dentro do horário de funcionamento e da "
                + "capacidade máxima cadastrada.",
            "Não deixar brasa acesa sem supervisão; descartar carvão e cinzas somente frios; não usar álcool "
                + "líquido para acender o fogo.")),
        Map.entry(AreaCategory.GOURMET_SPACE, new Content(
            "Uso reservado para eventos e refeições em grupo, dentro do horário de funcionamento e da capacidade "
                + "máxima cadastrada.",
            "Manter volume de som moderado; não furar paredes nem usar fita adesiva que danifique a pintura; "
                + "deixar bancadas e utensílios limpos ao final.")),
        Map.entry(AreaCategory.POOL, new Content(
            "Uso liberado apenas dentro do horário de funcionamento, respeitando a capacidade máxima e as normas "
                + "sanitárias do condomínio.",
            "Crianças sempre acompanhadas por adulto responsável; proibido recipiente de vidro na área molhada; "
                + "tomar ducha antes de entrar na piscina.")),
        Map.entry(AreaCategory.SPORTS_COURT, new Content(
            "Uso esportivo dentro do horário de funcionamento, respeitando a capacidade máxima e o revezamento "
                + "entre os moradores reservantes.",
            "Usar calçado adequado à modalidade; respeitar o horário reservado dos próximos usuários; retirar "
                + "objetos pessoais e bolas ao final.")),
        Map.entry(AreaCategory.TENNIS_COURT, new Content(
            "Uso esportivo dentro do horário de funcionamento, respeitando a capacidade máxima e o revezamento "
                + "entre os moradores reservantes.",
            "Usar calçado e vestimenta adequados; respeitar o horário reservado dos próximos usuários; recolher "
                + "bolas e equipamentos ao final.")),
        Map.entry(AreaCategory.GYM, new Content(
            "Uso individual para atividade física dentro do horário de funcionamento e da capacidade máxima "
                + "cadastrada.",
            "Usar toalha sobre os equipamentos; recolocar pesos e acessórios no lugar; higienizar o equipamento "
                + "após o uso.")),
        Map.entry(AreaCategory.GAME_ROOM, new Content(
            "Uso para jogos e recreação dentro do horário de funcionamento e da capacidade máxima cadastrada.",
            "Manter volume de som moderado; devolver jogos e acessórios ao lugar de origem; não consumir alimentos "
                + "sobre os equipamentos.")),
        Map.entry(AreaCategory.TOY_ROOM, new Content(
            "Uso infantil dentro do horário de funcionamento e da capacidade máxima cadastrada, sempre com "
                + "acompanhamento de um responsável.",
            "Crianças sempre acompanhadas por adulto responsável; guardar brinquedos ao final do uso; não permitir "
                + "alimentos dentro do espaço.")),
        Map.entry(AreaCategory.PLAYGROUND, new Content(
            "Uso infantil dentro do horário de funcionamento e da capacidade máxima cadastrada, sempre com "
                + "acompanhamento de um responsável.",
            "Crianças sempre acompanhadas por adulto responsável; respeitar a faixa etária recomendada dos "
                + "brinquedos; não usar o espaço com calçados sujos de areia fora da área apropriada.")),
        Map.entry(AreaCategory.SAUNA, new Content(
            "Uso individual ou em pequenos grupos dentro do horário de funcionamento e da capacidade máxima "
                + "cadastrada, respeitando o tempo de sessão recomendado.",
            "Usar toalha própria sobre os bancos; hidratar-se antes e depois do uso; comunicar a administração "
                + "sobre mal-estar imediatamente.")),
        Map.entry(AreaCategory.CINEMA, new Content(
            "Uso reservado para sessões de vídeo dentro do horário de funcionamento e da capacidade máxima "
                + "cadastrada.",
            "Manter volume de som moderado, sobretudo à noite; não consumir alimentos que sujem os assentos; "
                + "desligar os equipamentos ao final da sessão.")),
        Map.entry(AreaCategory.COWORKING, new Content(
            "Uso individual ou em pequenos grupos para estudo ou trabalho dentro do horário de funcionamento e da "
                + "capacidade máxima cadastrada.",
            "Manter silêncio ou volume de conversa baixo; liberar a mesa/sala ao final da reserva; não deixar "
                + "pertences pessoais fora do horário reservado.")),
        Map.entry(AreaCategory.PET_PLACE, new Content(
            "Uso para recreação de animais de estimação dentro do horário de funcionamento e da capacidade máxima "
                + "cadastrada.",
            "Manter o animal sempre sob supervisão do tutor; recolher os dejetos imediatamente; respeitar o limite "
                + "de animais por vez indicado pela administração.")),
        Map.entry(AreaCategory.OTHER, new Content(
            "Uso conforme a finalidade cadastrada pela administração, dentro do horário de funcionamento e da "
                + "capacidade máxima definida.",
            "Seguir as orientações específicas informadas pela administração para este espaço; deixar o local nas "
                + "mesmas condições encontradas."))
    );

    private AreaCategoryTemplates() {
    }

    public static String rulesTemplate(AreaCategory category) {
        return SPECIFIC.get(category).rules() + " " + LEGAL_BASIS + " " + NOTICE;
    }

    public static String conductTemplate(AreaCategory category) {
        return GENERAL_CONDUCT + " " + SPECIFIC.get(category).conduct() + " " + NOTICE;
    }
}
