-- F2-2: extensao unaccent, usada pela busca de unidades (RF-UNI-03) para
-- ignorar acentos/maiusculas ao buscar por numero, identificador ou nome de
-- morador (docs/03 "Unidades e moradores").
CREATE EXTENSION IF NOT EXISTS unaccent;
