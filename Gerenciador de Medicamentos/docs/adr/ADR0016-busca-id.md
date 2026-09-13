# ADR-0016 — Busca de usuário e medicamento por id (`buscarPorId`)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Não havia forma de localizar um usuário ou medicamento específico pelo id — só existiam `listarTodos()` e, para usuário, `buscarPorNome()`.
- **Por que esse problema é importante?** Operações de edição (como o `EditarMedicamentoService`, ver ADR-0017) e de validação (como o `RegistrarMedicamentoService`, ver ADR-0018) precisam localizar um registro específico a partir de um id conhecido, não de um nome.
- **Limitações e requisitos envolvidos:** o id é único por tipo de entidade, então a busca por id deveria, na prática, sempre encontrar no máximo um resultado.
- **Situação atual a modificar:** `SalvarUsuarioPort` e `SalvarMedicamentoPort` não tinham nenhum método de busca por id.

## Decisão

Adicionar `buscarPorId(int id)` às portas `SalvarUsuarioPort` e `SalvarMedicamentoPort`, implementado nos respectivos adapters como um filtro sobre `listarTodos()`, devolvendo uma lista (mesmo esperando no máximo um resultado).

- **O que será utilizado ou alterado?** Novo método nas duas portas de saída e suas implementações CSV.
- **Como a solução será aplicada?** Reaproveita `listarTodos()` internamente, filtrando pelo id exato.
- **Por que essa alternativa foi escolhida?** Segue o mesmo padrão já estabelecido por `buscarPorNome` (ADR-0010), mantendo consistência entre os métodos de busca.

## Alternativas consideradas

### Alternativa 1 — Filtro sobre `listarTodos()`, devolvendo `List<T>`

**Vantagens:**
- Consistente com o padrão já usado em `buscarPorNome`.
- Simples de implementar e testar.

**Desvantagens:**
- Devolver uma lista para uma busca que, por definição, tem no máximo um resultado, obriga quem usa a lidar com `.get(0)` e checar `isEmpty()`, em vez de receber diretamente um valor único ou nulo.

### Alternativa 2 — Devolver `Optional<T>` (um único resultado ou vazio)

**Vantagens:**
- Expressa melhor a semântica real da busca por id: zero ou um resultado, nunca mais que isso.
- Evita o padrão `.get(0)` que assume implicitamente que a lista não está vazia.

**Desvantagens:**
- Diferente do padrão já estabelecido pelas outras buscas (`buscarPorNome`, que devolve lista de propósito, pois pode ter vários resultados).

### Alternativa 3 — Lançar exceção diretamente dentro do `buscarPorId` se não encontrar

**Vantagens:**
- Quem chama não precisa checar `isEmpty()` — ou recebe o objeto, ou recebe uma exceção.

**Desvantagens:**
- Mistura a responsabilidade de "buscar dados" com a de "decidir se a ausência é um erro" — em alguns contextos, não encontrar nada é uma situação válida, não um erro.

## Justificativa

- **Consistência:** manter o mesmo formato de retorno (`List<T>`) entre `buscarPorNome` e `buscarPorId` evita que quem usa a porta precise lembrar de duas convenções diferentes.
- **Facilidade de desenvolvimento:** reaproveita a leitura já existente via `listarTodos()`, sem exigir lógica de busca nova.
- **Compatibilidade com o projeto:** mantém os adapters simples, sem introduzir tipos como `Optional` que ainda não são usados em nenhum outro lugar do projeto.

## Consequências

**Positivas**
- Viabiliza diretamente a implementação real do `EditarMedicamentoService` (ADR-0017) e a validação de dono no `RegistrarMedicamentoService` (ADR-0018).
- Segue um padrão já conhecido, reduzindo a curva de entendimento do código.

**Negativas**
- Quem usa `buscarPorId` precisa lembrar de checar `isEmpty()` antes de acessar `.get(0)` — um esquecimento aqui gera `IndexOutOfBoundsException` em vez de um erro mais claro.

## Impactos

Afeta `domain/port/out/SalvarUsuarioPort`, `domain/port/out/SalvarMedicamentoPort` e seus adapters em `adapter/out/persistence/`.

## Implementação

1. Adicionar `buscarPorId(int id)` às duas portas.
2. Implementar nos adapters, filtrando `listarTodos()` pelo id.
3. Usar esse método nos pontos que precisam localizar um registro específico (edição, validação de dono).

## Observações

Se esse padrão de "lista que deveria ter no máximo um item" se repetir bastante conforme o projeto crescer, vale reconsiderar a Alternativa 2 (`Optional<T>`) para expressar a semântica de forma mais precisa.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro