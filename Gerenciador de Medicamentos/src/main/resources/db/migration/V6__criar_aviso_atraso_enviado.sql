-- Guarda quais atrasos já viraram push, para o mesmo remédio esquecido não ser avisado de novo a cada verificação.
CREATE TABLE aviso_atraso_enviado (
    medicamento_id INTEGER     NOT NULL,
    dia            DATE        NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (medicamento_id, dia)
);
