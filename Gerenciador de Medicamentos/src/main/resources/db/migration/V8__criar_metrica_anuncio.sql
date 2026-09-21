-- Contagens anônimas dos banners: por dia, banner, posição na tela e perfil (IDOSO, FAMILIAR ou VISITANTE).
-- Não guarda quem viu ou tocou: só somas.
CREATE TABLE metrica_anuncio (
    dia        DATE        NOT NULL,
    anuncio_id VARCHAR(64) NOT NULL,
    posicao    VARCHAR(32) NOT NULL,
    perfil     VARCHAR(16) NOT NULL,
    exibicoes  BIGINT      NOT NULL DEFAULT 0,
    cliques    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (dia, anuncio_id, posicao, perfil)
);
