-- Uma tomada registrada (foi_tomado = true) por remédio por dia (ADR-0048).
-- A checagem "já tomou hoje" do serviço não basta: dois pedidos ao mesmo tempo (duplo toque, reenvio do app offline)
-- passariam os dois. Este índice faz o banco recusar o segundo.
CREATE UNIQUE INDEX ux_historico_tomada_por_dia
    ON historico (medicamento_id, ((data_hora_tomada)::date))
    WHERE foi_tomado;
