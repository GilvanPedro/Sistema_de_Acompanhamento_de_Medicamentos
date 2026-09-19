-- Chaves de idempotência do cadastro de remédio (ADR-0049, uso sem internet).
-- O app manda uma chave por remédio cadastrado; reenviar a mesma chave devolve o remédio já criado em vez de duplicá-lo.
CREATE TABLE chave_idempotencia (
    usuario_id INTEGER     NOT NULL,
    chave      VARCHAR(64) NOT NULL,
    recurso_id INTEGER     NOT NULL,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, chave)
);

CREATE INDEX ix_chave_idempotencia_criado_em ON chave_idempotencia (criado_em);
