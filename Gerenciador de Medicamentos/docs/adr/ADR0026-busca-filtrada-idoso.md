# ADR-0026 — Busca de histórico filtrada por idoso

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Não havia forma de obter apenas os registros de histórico de um idoso específico — só existia `listarTodos()`, que devolve o histórico inteiro do sistema, exigindo filtrar manualmente depois.
- **Por que esse problema é importante?** Consultar "o que esse idoso tomou ou deixou de tomar" é um caso de uso natural do sistema (para o próprio idoso, para um familiar, ou para uma futura tela de acompanhamento), e não deveria exigir carregar e descartar dados de todos os outros idosos.
- **Limitações e requisitos envolvidos:** o histórico já depende de mapas de idosos e medicamentos carregados previamente (ADR-0009/0014) para reconstruir os objetos completos — a busca filtrada precisa manter essa mesma dependência.
- **Situação atual a modificar:** `SalvarHistoricoPort` não tinha nenhum método de busca filtrada por idoso.

## Decisão

Adicionar `listarPorIdoso(int idosoId, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos)` a `SalvarHistoricoPort`, reaproveitando o `montarObjeto` já existente e filtrando o resultado pelo id do idoso. Criar também `BuscarHistoricoPorIdosoCase`/`BuscarHistoricoPorIdosoService`, que resolve o `Idoso` pelo id, monta o mapa de medicamentos necessário, e chama a busca filtrada — poupando quem usa o caso de uso de montar esses mapas manualmente.

- **O que será utilizado ou alterado?** Novo método em `SalvarHistoricoPort` e implementação em `HistoricoCsvAdapter`; nova porta de entrada `BuscarHistoricoPorIdosoCase` e serviço `BuscarHistoricoPorIdosoService`; novo método em `AppConfig`.
- **Como a solução será aplicada?** O adapter percorre as linhas do arquivo, reconstrói cada uma via `montarObjeto` (já existente) e mantém só as que pertencem ao idoso pedido. O serviço orquestra a busca do idoso e a montagem do mapa de medicamentos antes de delegar ao adapter.
- **Por que essa alternativa foi escolhida?** Reaproveita a lógica de reconstrução já testada (`montarObjeto`), sem duplicar parsing, e mantém a mesma dependência de mapas já estabelecida pelo restante do `SalvarHistoricoPort`.

## Alternativas consideradas

### Alternativa 1 — Filtrar reaproveitando `montarObjeto`, no próprio adapter

**Vantagens:**
- Sem duplicação de lógica de parsing — reaproveita o método que já existe e já é usado por `listarTodos`.
- Consistente com a assinatura já estabelecida para o histórico (recebendo os mapas de idosos e medicamentos).

**Desvantagens:**
- Ainda lê o arquivo inteiro, mesmo quando o idoso pedido tem poucos registros — não há como evitar isso sem um índice separado.

### Alternativa 2 — Filtrar em memória, chamando `listarTodos()` e depois um `.stream().filter(...)` de fora do adapter

Deixar a filtragem inteiramente do lado de quem consome a porta, sem adicionar método novo nela.

**Vantagens:**
- Nenhuma mudança na porta `SalvarHistoricoPort`.

**Desvantagens:**
- Obriga quem quiser essa busca a reimplementar o filtro toda vez, em vez de ter um método pronto e nomeado para esse propósito.
- Não é mais eficiente do que a Alternativa 1 (ainda lê tudo), só menos organizado.

### Alternativa 3 — Índice separado (idosoId → lista de ids de histórico), atualizado a cada `salvar`

Manter uma estrutura auxiliar que aceleraria a busca, evitando reler linhas de outros idosos.

**Vantagens:**
- Busca mais rápida para uma base de histórico grande.

**Desvantagens:**
- Complexidade adicional desnecessária para o volume de dados atual do projeto — mesmo raciocínio já aplicado em decisões anteriores (ADR-0010).

## Justificativa

- **Facilidade de desenvolvimento:** reaproveita `montarObjeto`, sem introduzir lógica de parsing nova.
- **Compatibilidade com o projeto:** mantém a mesma convenção de dependência de mapas já usada em `listarTodos`/`atualizar` do histórico.
- **Manutenibilidade:** o serviço `BuscarHistoricoPorIdosoService` poupa quem consome o caso de uso de conhecer o detalhe de que o histórico precisa de mapas de idosos e medicamentos — essa orquestração fica isolada num único lugar.

## Consequências

**Positivas**
- Consultar o histórico de um idoso específico virou uma chamada única (`buscarHistoricoDoIdoso(idosoId)`), sem precisar montar mapas manualmente.
- Reaproveita a validação de tipo (`instanceof Idoso`) já usada em outros pontos do projeto, rejeitando um id de `Familiar` passado por engano.

**Negativas**
- A busca ainda lê o arquivo de histórico inteiro internamente — o ganho é organizacional (reaproveitar `montarObjeto`, esconder a orquestração), não de desempenho puro.

## Impactos

Afeta `domain/port/out/SalvarHistoricoPort` (novo método), `adapter/out/persistence/HistoricoCsvAdapter` (implementação), `domain/port/in/BuscarHistoricoPorIdosoCase` (novo), `application/service/BuscarHistoricoPorIdosoService` (novo) e `config/AppConfig` (novo método de montagem).

## Implementação

1. Adicionar `listarPorIdoso` a `SalvarHistoricoPort`.
2. Implementar em `HistoricoCsvAdapter`, reaproveitando `montarObjeto` e filtrando pelo idoso.
3. Criar `BuscarHistoricoPorIdosoCase` e `BuscarHistoricoPorIdosoService`.
4. Adicionar `criarBuscarHistoricoPorIdosoService()` ao `AppConfig`.
5. Testar buscando o histórico de um idoso com registros, um sem nenhum registro, e um id que pertence a um familiar (deve rejeitar).

## Observações

Se a leitura completa do arquivo de histórico se tornar um gargalo real (base de dados grande), a Alternativa 3 (índice auxiliar) deve ser revisitada — mesma ressalva já registrada em decisões anteriores sobre performance de leitura CSV (ADR-0014, ADR-0015).

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro