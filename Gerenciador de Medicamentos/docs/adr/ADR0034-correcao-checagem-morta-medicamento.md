# ADR-0034 — Correção da checagem morta de dono inexistente no cadastro de medicamento

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `RegistrarMedicamentoService` checava `if (encontrado == null)` depois de chamar `salvarUsuarioPort.buscarPorId(idosoId)`, mas desde a ADR-0021, `buscarPorId` nunca devolve `null` — ele lança `NoSuchElementException` quando não encontra. Essa checagem nunca era executada.
- **Por que esse problema é importante?** Cadastrar um medicamento com um `idosoId` inexistente não produzia a mensagem de erro clara e específica que o código sugeria (`"Não existe usuário cadastrado com id..."`) — em vez disso, o erro que de fato subia era o `NoSuchElementException` genérico do adapter, sem passar pela intenção original do código.
- **Limitações e requisitos envolvidos:** este é o mesmo padrão de bug já corrigido duas vezes antes (`EditarUsuarioService`, `EditarMedicamentoService`) — checagem de `null` que ficou órfã depois que `buscarPorId` mudou de contrato (ADR-0019/0021).
- **Situação atual a modificar:** o bloco de checagem `if (encontrado == null)` em `RegistrarMedicamentoService.registrarMedicamento`.

## Decisão

Envolver a chamada a `buscarPorId` num `try/catch`, convertendo `NoSuchElementException` em `UsuarioNaoEncontradoException`, removendo a checagem de `null` que nunca era alcançada.

- **O que será utilizado ou alterado?** `RegistrarMedicamentoService`, extraindo a busca do idoso para um método privado `buscarUsuario`, no mesmo padrão já usado em `EditarUsuarioService` e `EditarMedicamentoService`.
- **Como a solução será aplicada?** `buscarUsuario` captura `NoSuchElementException` e relança como `UsuarioNaoEncontradoException`; o restante da lógica (checar se o resultado é uma instância de `Idoso`) continua igual.
- **Por que essa alternativa foi escolhida?** Mantém consistência com o padrão já estabelecido nos outros dois serviços que fazem a mesma tradução de exceção, em vez de inventar uma terceira forma de lidar com o mesmo problema.

## Alternativas consideradas

### Alternativa 1 — `try/catch` convertendo para `UsuarioNaoEncontradoException`, seguindo o padrão já usado

**Vantagens:**
- Consistente com `EditarUsuarioService` e `EditarMedicamentoService`, que já resolvem o mesmo problema da mesma forma.
- Corrige a checagem morta sem introduzir um padrão novo no projeto.

**Desvantagens:**
- Nenhuma desvantagem relevante identificada — é a correção mais direta do problema.

### Alternativa 2 — Deixar `NoSuchElementException` subir sem tradução

**Vantagens:**
- Menos código.

**Desvantagens:**
- Perde a mensagem de erro mais específica e nomeada que `UsuarioNaoEncontradoException` fornece.
- Inconsistente com o padrão já adotado nos outros dois serviços.

### Alternativa 3 — Voltar `buscarPorId` a devolver `null` em vez de lançar exceção

Reverter a decisão da ADR-0019/0021, eliminando a causa raiz do problema.

**Vantagens:**
- Eliminaria de vez esse tipo de checagem morta, já que `null` seria o sinal esperado novamente.

**Desvantagens:**
- Reverteria uma decisão já tomada e justificada (ADR-0019), reintroduzindo o problema que ela resolvia (checagem de ausência espalhada e opcional em cada lugar que usa `buscarPorId`).

## Justificativa

- **Consistência:** o mesmo padrão de tradução de exceção já existe em dois outros serviços — usar o terceiro caso reforça uma convenção única no projeto, em vez de três formas diferentes de lidar com "não encontrado".
- **Manutenibilidade:** erros de cadastro com id inexistente agora produzem a mensagem correta e específica, facilitando diagnóstico.
- **Compatibilidade com o projeto:** não reabre a decisão já tomada e validada na ADR-0019/0021 sobre o contrato de `buscarPorId`.

## Consequências

**Positivas**
- Cadastrar um medicamento com `idosoId` inexistente agora produz a mensagem de erro pretendida desde o início.
- Elimina mais uma instância do bug de "checagem morta" que já apareceu duas vezes antes no projeto.

**Negativas**
- Nenhuma identificada — correção direta, sem trade-off relevante.

## Impactos

Afeta `application/service/RegistrarMedicamentoService`.

## Implementação

1. Extrair a busca do idoso para um método privado `buscarUsuario(int idosoId)`, com `try/catch` convertendo `NoSuchElementException` em `UsuarioNaoEncontradoException`.
2. Remover a checagem `if (encontrado == null)` do corpo de `registrarMedicamento`.
3. Testar cadastrando um medicamento com um `idosoId` inexistente, confirmando que a exceção correta é lançada com a mensagem esperada.

## Observações

Esse é o terceiro caso do mesmo padrão de bug encontrado no projeto (checagem de `null` órfã após a mudança de contrato de `buscarPorId`). Vale, ao revisar qualquer código escrito antes da ADR-0019/0021, procurar especificamente por esse padrão em outros lugares que ainda não foram auditados.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro