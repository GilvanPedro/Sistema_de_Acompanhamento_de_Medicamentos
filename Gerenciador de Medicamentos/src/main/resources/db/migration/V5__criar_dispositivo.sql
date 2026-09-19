-- Aparelhos que recebem push (token do Firebase). Um token pertence a uma conta por vez.
CREATE TABLE dispositivo (
    token         VARCHAR(400) PRIMARY KEY,
    usuario_id    INTEGER      NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_dispositivo_usuario ON dispositivo (usuario_id);
