# ADR-0019 — `buscarPorId` retorna o objeto direto e lança exceção quando não encontrado

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** A ADR-0016 definiu `buscarPorId` devolvendo `List<T>`, mesmo sabendo que o id é único e a busca nunca teria mais de um resultado. Isso obrigava quem chamava a lidar com `.isEmpty()` e `.get(0)` toda vez, um padrão verboso para uma busca que é, por natureza, de zero ou um resultado.
- **Por que esse problema é importante?** Usar `.get(0)` sem checar antes se a lista está vazia é uma fonte comum de erro (`IndexOutOfBoundsException`), como aconteceu na primeira versão do `MedicamentoCsvAdapter.buscarPorId`, que fazia exatamente isso sem proteção.
- **Limitações e requisitos envolvidos:** a mudança precisa decidir o que acontece quando o id buscado não existe — as opções são devolver `null` ou lançar uma exceção diretamente no adapter.
- **Situação atual a modificar:** `SalvarMedicamentoPort.buscarPorId` devolvia `List<Medicamento>`; o `SalvarUsuarioPort.buscarPorId` equivalente ainda não existia neste padrão e está planejado para seguir a mesma mudança.

## Decisão

Alterar `buscarPorId` para devolver o objeto diretamente (`Medicamento`, e futuramente `Usuario`), lançando uma exceção de domínio (`MedicamentoNaoEncontradoException`) quando o id não for encontrado, em vez de devolver `null` ou uma lista vazia.

- **O que será utilizado ou alterado?** Assinatura de `SalvarMedicamentoPort.buscarPorId` (de `List<Medicamento>` para `Medicamento`), implementação no `MedicamentoCsvAdapter`, nova classe `MedicamentoNaoEncontradoException` em `domain/exception/`, e simplificação do `EditarMedicamentoService`, que deixa de checar `null`/`isEmpty()`.
- **Como a solução será aplicada?** O adapter percorre `listarTodos()`; se encontrar o id, devolve o objeto; se não encontrar, lança a exceção antes de devolver qualquer coisa.
- **Por que essa alternativa foi escolhida?** Como todo uso atual de `buscarPorId` no projeto espera que o id exista (edição, validação de dono), tratar a ausência como erro automático evita que cada chamador precise reimplementar essa checagem.

## Alternativas consideradas

### Alternativa 1 — Retorno direto do objeto, lançando exceção de domínio quando não encontrado

**Vantagens:**
- Elimina o `.isEmpty()`/`.get(0)` repetido em cada lugar que usa a busca.
- Centraliza o tratamento de "não encontrado" num único ponto (o adapter), garantindo que nenhum chamador esqueça de tratar essa ausência.

**Desvantagens:**
- Obriga qualquer uso futuro onde "não encontrar" seja uma resposta válida (não um erro) a usar `try/catch` para um caso que não é excepcional.

### Alternativa 2 — Retorno direto do objeto, devolvendo `null` quando não encontrado

**Vantagens:**
- Cada chamador decide livremente o que fazer com a ausência — tratar como erro ou como caso normal.

**Desvantagens:**
- Depende de quem chama lembrar de checar `null` antes de usar o resultado — um esquecimento aqui gera `NullPointerException`, um erro tão confuso quanto o `IndexOutOfBoundsException` que a mudança pretendia evitar.

### Alternativa 3 — Retorno via `Optional<T>`

Devolver `Optional<Medicamento>`, expressando explicitamente no tipo que o resultado pode não existir.

**Vantagens:**
- Torna a possível ausência visível na própria assinatura do método, sem depender de documentação ou convenção.
- Evita tanto o `NullPointerException` da Alternativa 2 quanto o `try/catch` forçado da Alternativa 1.

**Desvantagens:**
- Introduziria um tipo (`Optional`) ainda não usado em nenhum outro lugar do projeto, quebrando a consistência de estilo já estabelecida.

## Justificativa

- **Compatibilidade com o projeto:** todos os usos atuais de `buscarPorId` (edição de medicamento, validação de dono no cadastro) tratam "não encontrado" como um erro de negócio genuíno, não como um caso normal — a Alternativa 1 reflete isso diretamente.
- **Manutenibilidade:** centraliza a checagem de existência no adapter, evitando repetir essa lógica em cada serviço que usa a busca.
- **Facilidade de desenvolvimento:** simplifica os serviços que consomem `buscarPorId`, removendo verificações repetidas de `isEmpty()`/`null`.
- **Testabilidade:** fica fácil testar o caso de "id não encontrado" verificando se a exceção correta é lançada, em vez de checar um valor `null` ou uma lista vazia.

## Consequências

**Positivas**
- `EditarMedicamentoService` ficou mais enxuto, sem checagem manual de ausência.
- Erros de "id não encontrado" agora têm um tipo de exceção específico e nomeado, em vez de mensagens genéricas ou `IndexOutOfBoundsException`.

**Negativas**
- Qualquer código que chame `buscarPorId` esperando que "não encontrado" seja um resultado normal (não um erro) precisará de `try/catch`, o que é mais verboso do que checar `null`.
- A mudança de `SalvarUsuarioPort.buscarPorId` para o mesmo padrão ainda está pendente — até lá, as duas portas de busca por id do projeto seguem convenções diferentes.

## Impactos

Afeta `domain/port/out/SalvarMedicamentoPort` (assinatura alterada), `adapter/out/persistence/MedicamentoCsvAdapter` (implementação), nova classe `domain/exception/MedicamentoNaoEncontradoException`, `application/service/EditarMedicamentoService` (simplificado) e qualquer código que chamasse `buscarPorId` esperando uma lista (como o `Teste2.java`, arquivo de teste solto).

## Implementação

1. Criar `MedicamentoNaoEncontradoException` em `domain/exception/`.
2. Alterar a assinatura de `SalvarMedicamentoPort.buscarPorId` para devolver `Medicamento` diretamente.
3. Ajustar `MedicamentoCsvAdapter.buscarPorId` para lançar a exceção quando não encontrar, em vez de devolver lista vazia.
4. Remover a checagem `isEmpty()`/`get(0)` do `EditarMedicamentoService`, já que o adapter agora garante um retorno válido ou uma exceção.
5. Ajustar qualquer outro código que ainda espere uma lista do `buscarPorId` (como o `Teste2.java`).
6. Repetir o mesmo padrão em `SalvarUsuarioPort.buscarPorId` quando essa mudança for feita (planejada, ainda não implementada).

## Observações

Esta ADR também define o padrão a ser seguido quando `SalvarUsuarioPort.buscarPorId` for atualizado — a intenção é manter a mesma convenção (retorno direto + exceção de domínio) nas duas portas, evitando duas formas diferentes de lidar com "não encontrado" dentro do mesmo projeto. Revisa parcialmente a decisão da ADR-0016, que havia optado por manter o retorno em lista por consistência com `buscarPorNome` — aqui, a consistência interna entre as buscas por id (medicamento e usuário) foi priorizada sobre a consistência com a busca por nome, que continua, de propósito, podendo ter vários resultados.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro