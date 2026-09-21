-- Banners de anúncio (patrocinadores), com a imagem dentro do banco. Cadastrados pelo painel (/painel/banners).
-- Enquanto esta tabela estiver vazia, o app mostra só o banner de exemplo que acompanha o projeto.
CREATE TABLE banner (
    id            VARCHAR(64)  PRIMARY KEY,
    empresa       VARCHAR(120) NOT NULL,
    texto         VARCHAR(200) NOT NULL DEFAULT '',
    link          VARCHAR(500),
    peso          NUMERIC(6,2) NOT NULL DEFAULT 1 CHECK (peso >= 0 AND peso <= 100),
    tipo_imagem   VARCHAR(20)  NOT NULL,
    imagem        BYTEA        NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
