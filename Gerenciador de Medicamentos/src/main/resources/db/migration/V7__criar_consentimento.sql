-- Aceite da política de privacidade (LGPD): qual versão do texto cada pessoa aceitou e quando.
CREATE TABLE consentimento (
    usuario_id INTEGER     NOT NULL,
    versao     VARCHAR(20) NOT NULL,
    aceito_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, versao)
);
