-- Tokens de renovação da API (ADR-0047). Só o hash SHA-256 do token é guardado;
-- o token em si existe apenas no aparelho do usuário.
CREATE TABLE refresh_token (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   INTEGER     NOT NULL REFERENCES usuario (id),
    token_hash   CHAR(64)    NOT NULL UNIQUE,
    expira_em    TIMESTAMPTZ NOT NULL,
    revogado_em  TIMESTAMPTZ,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_token_usuario ON refresh_token (usuario_id);
