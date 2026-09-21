-- Links de "esqueci minha senha". Só o hash do token fica no banco (o token em si vai apenas para o e-mail da pessoa),
-- então quem lesse o banco não conseguiria redefinir a senha de ninguém. Cada link vale 30 minutos e só uma vez.
CREATE TABLE redefinicao_senha (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  INTEGER     NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_redefinicao_senha_hash ON redefinicao_senha (token_hash);
CREATE INDEX ix_redefinicao_senha_usuario ON redefinicao_senha (usuario_id);
