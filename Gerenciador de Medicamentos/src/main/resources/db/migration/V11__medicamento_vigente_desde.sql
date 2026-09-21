-- Desde quando o horário atual do remédio vale. O histórico usa isso para marcar "não tomou" só nos horários que já
-- existiam: sem essa data, um remédio cadastrado hoje apareceria como "não tomado" em todas as semanas passadas.
-- Os remédios que já existem começam a valer agora (não inventamos faltas do passado). Trocar o dia ou o horário
-- do remédio reinicia a data; trocar só o nome ou o tipo, não.
ALTER TABLE medicamento ADD COLUMN vigente_desde TIMESTAMPTZ NOT NULL DEFAULT now();
