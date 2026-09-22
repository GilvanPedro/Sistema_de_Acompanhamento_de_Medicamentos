-- Links de "excluir minha conta", pedidos pela página pública (sem precisar abrir o app nem lembrar a senha).
-- Mesmo desenho da V12 (redefinicao_senha): só o hash do token fica no banco. Tabela própria, e não a mesma da
-- redefinição de senha, para um link vazado de uma finalidade nunca servir para a outra.
CREATE TABLE exclusao_conta (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  INTEGER     NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_exclusao_conta_hash ON exclusao_conta (token_hash);
CREATE INDEX ix_exclusao_conta_usuario ON exclusao_conta (usuario_id);
