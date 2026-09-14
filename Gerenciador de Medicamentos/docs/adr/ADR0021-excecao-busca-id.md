# ADR-0021 — Extensão do padrão `buscarPorId` (retorno direto + exceção) para usuário

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** A ADR-0019 mudou `buscarPorId` de medicamento para devolver o objeto diretamente, lançando exceção quando não encontrado, mas deixou como pendência aplicar o mesmo padrão em `SalvarUsuarioPort`.
- **Por que esse problema é importante?** Ter duas convenções diferentes de busca por id dentro do mesmo projeto (uma devolvendo lista, outra devolvendo objeto direto) obriga quem lê o código a lembrar qual porta segue qual padrão.
- **Limitações e requisitos envolvidos:** o `EditarUsuarioService` (ADR-0020) depende de buscar um `Usuario` por id para poder editá-lo — precisa de um contrato claro sobre o que acontece quando o id não existe.
- **Situação atual a modificar:** `SalvarUsuarioPort` não tinha `buscarPorId`; foi adicionado já seguindo o padrão definido na ADR-0019 (retorno direto), fechando a pendência registrada naquela ADR.

## Decisão

Adicionar `buscarPorId(int id)` a `SalvarUsuarioPort`, devolvendo `Usuario` diretamente, e implementar no `UsuarioCsvAdapter` lançando `NoSuchElementException` quando o id não for encontrado — mesma convenção adotada para medicamento na ADR-0019, mas usando a exceção genérica do Java em vez de criar uma exceção de domínio própria.

- **O que será utilizado ou alterado?** Novo método na porta `SalvarUsuarioPort` e implementação no `UsuarioCsvAdapter`.
- **Como a solução será aplicada?** Percorre `listarTodos()`; devolve o usuário se o id bater; lança `NoSuchElementException` se percorrer tudo sem encontrar.
- **Por que essa alternativa foi escolhida?** Mantém a consistência de retorno direto já estabelecida para medicamento, mas usa uma exceção pronta do Java em vez de criar uma exceção de domínio (`UsuarioNaoEncontradoException`) sem um motivo concreto para essa diferenciação ainda existir.

## Alternativas consideradas

### Alternativa 1 — Retorno direto, lançando `NoSuchElementException` (exceção genérica do Java)

**Vantagens:**
- Nenhuma classe nova para manter.
- Suficiente para o uso atual, já que nenhum código do projeto ainda precisa distinguir esse erro de outros para tratá-lo de forma diferente.

**Desvantagens:**
- Menos expressivo que uma exceção nomeada especificamente para o domínio — quem lê `NoSuchElementException` não sabe, só pelo tipo, que se trata de "usuário não encontrado" sem ler a mensagem.

### Alternativa 2 — Retorno direto, lançando uma exceção de domínio própria (`UsuarioNaoEncontradoException`)

Mesma abordagem usada como opção na ADR-0019, criando uma classe específica em `domain/exception/`.

**Vantagens:**
- Mais expressivo — o tipo da exceção já comunica o problema, sem depender da mensagem.
- Permite tratamento diferenciado no futuro (por exemplo, mapear para um código HTTP 404 numa futura API), sem precisar inspecionar a mensagem de texto.

**Desvantagens:**
- Mais uma classe para manter, sem benefício prático imediato — nenhum código do projeto hoje precisa distinguir esse erro de outros `RuntimeException`.

### Alternativa 3 — Manter `buscarPorId` de usuário devolvendo `List<Usuario>`, diferente do padrão adotado para medicamento

**Vantagens:**
- Nenhuma vantagem real — só descartada por completude, já que contraria diretamente o objetivo desta ADR.

**Desvantagens:**
- Perpetua a inconsistência entre as duas portas de busca por id, o problema que esta ADR busca resolver.

## Justificativa

- **Consistência:** fecha a pendência deixada pela ADR-0019, unificando a convenção de `buscarPorId` entre medicamento e usuário.
- **Custo:** criar uma exceção de domínio própria (Alternativa 2) sem um caso de uso concreto que a justifique é esforço adiantado sem necessidade comprovada — decisão consciente de não sobre-engenheirar.
- **Manutenibilidade:** `EditarUsuarioService` (ADR-0020) se beneficia diretamente do contrato claro: ou recebe o usuário, ou a exceção já foi lançada antes.

## Consequências

**Positivas**
- As duas portas de busca por id (`SalvarMedicamentoPort` e `SalvarUsuarioPort`) agora seguem exatamente a mesma convenção.
- `EditarUsuarioService` não precisa de nenhuma checagem própria de "usuário não encontrado" — herda essa garantia do adapter.

**Negativas**
- A exceção lançada (`NoSuchElementException`) é genérica — se no futuro o projeto precisar tratar "usuário não encontrado" de forma diferente de outros erros de busca, será necessário revisar essa decisão e migrar para uma exceção de domínio própria.

## Impactos

Afeta `domain/port/out/SalvarUsuarioPort` (novo método) e `adapter/out/persistence/UsuarioCsvAdapter` (implementação).

## Implementação

1. Adicionar `buscarPorId(int id)` a `SalvarUsuarioPort`.
2. Implementar no `UsuarioCsvAdapter`, lançando `NoSuchElementException` quando não encontrado.
3. Usar esse método em `EditarUsuarioService` (ADR-0020) e em qualquer outro ponto que precise localizar um usuário específico por id.

## Observações

Se o projeto crescer a ponto de precisar tratar diferentemente um "usuário não encontrado" de um "medicamento não encontrado" (por exemplo, numa futura API REST que precise devolver códigos de erro diferentes), vale revisitar esta decisão e a da ADR-0019 juntas, migrando ambas para exceções de domínio nomeadas.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro