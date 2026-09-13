# ADR-0018 — Validação do idoso dono ao cadastrar medicamento

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Desde que `Medicamento` ganhou o campo `idosoId` (ADR-0012), o `RegistrarMedicamentoService` aceitava qualquer valor de `idosoId` sem checar se ele correspondia a um idoso de verdade — um id inexistente, ou até o id de um `Familiar`, passava sem erro.
- **Por que esse problema é importante?** Um medicamento com um dono inválido quebra silenciosamente funcionalidades que dependem dessa relação, como a verificação de atraso (ADR-0014), que precisa localizar o `Idoso` correspondente para notificar.
- **Limitações e requisitos envolvidos:** o `idosoId` precisa corresponder a um usuário existente, e esse usuário precisa ser especificamente um `Idoso` — um `Familiar` não pode ser dono de medicamento.
- **Situação atual a modificar:** `RegistrarMedicamentoService.registrarMedicamento` criava e salvava o medicamento imediatamente, sem nenhuma checagem sobre o `idosoId` recebido.

## Decisão

Adicionar, dentro de `RegistrarMedicamentoService`, uma checagem que busca o `idosoId` recebido via `SalvarUsuarioPort.buscarPorId`, lançando `IllegalArgumentException` se o id não existir ou se existir mas pertencer a um `Familiar` em vez de um `Idoso`.

- **O que será utilizado ou alterado?** `RegistrarMedicamentoService` ganha uma nova dependência, `SalvarUsuarioPort`, usada só para essa validação.
- **Como a solução será aplicada?** Antes de criar o `Medicamento`, busca o usuário pelo id; se a lista vier vazia, lança exceção de "usuário não encontrado"; se vier um `Familiar`, lança exceção específica informando que apenas idosos podem ter medicamentos.
- **Por que essa alternativa foi escolhida?** Garante a integridade do dado no momento do cadastro, que é o ponto mais barato para prevenir um erro — impedir a entrada de um dado inválido é mais simples do que lidar com as consequências dele mais adiante no sistema.

## Alternativas consideradas

### Alternativa 1 — Validar o dono dentro do próprio `RegistrarMedicamentoService`, no momento do cadastro

**Vantagens:**
- Impede que um medicamento com dono inválido chegue a ser persistido.
- O erro aparece o mais cedo possível, no ponto exato onde o dado é fornecido.

**Desvantagens:**
- Acopla o `RegistrarMedicamentoService` a `SalvarUsuarioPort`, uma porta que, a rigor, pertence a outro contexto (usuário, não medicamento).

### Alternativa 2 — Validar só na hora de usar o vínculo (ex: na verificação de atraso)

Deixar o cadastro aceitar qualquer `idosoId`, e cada consumidor do dado (como o `VerificarAtrasoMedicamentoService`) lidar com a possibilidade de o idoso não existir.

**Vantagens:**
- `RegistrarMedicamentoService` continua simples, sem depender de `SalvarUsuarioPort`.

**Desvantagens:**
- Adia o problema para múltiplos pontos de consumo, exigindo que cada um trate a ausência do idoso — mais chances de esquecer essa checagem em algum lugar.
- Permite que dados inválidos fiquem persistidos por tempo indeterminado antes de alguém notar.

### Alternativa 3 — Validação via evento assíncrono, verificando a integridade periodicamente

Um processo separado, rodando de tempos em tempos, verificando se todos os medicamentos têm donos válidos e sinalizando inconsistências.

**Vantagens:**
- Desacopla completamente o cadastro da validação.

**Desvantagens:**
- Complexidade desnecessária para o problema — permite que dados inválidos existam temporariamente até a próxima verificação, quando prevenir na entrada já resolveria de vez.

## Justificativa

- **Manutenibilidade:** validar na entrada evita que o problema se manifeste de forma confusa mais adiante (por exemplo, um `NullPointerException` dentro da verificação de atraso, sem pista clara da causa raiz).
- **Segurança dos dados:** garante que a relação medicamento-idoso, central para várias funcionalidades (notificação, verificação de atraso), seja sempre válida desde a criação.
- **Compatibilidade com o projeto:** segue o espírito da ADR-0005 (cadastro validado), ainda que essa validação específica não estivesse ligada até agora.

## Consequências

**Positivas**
- Impossível cadastrar um medicamento com `idosoId` inexistente ou pertencente a um familiar.
- Erros de cadastro aparecem imediatamente, com mensagem clara, em vez de causarem falhas silenciosas mais adiante.

**Negativas**
- `RegistrarMedicamentoService` agora depende de `SalvarUsuarioPort`, uma porta de outro contexto — um acoplamento que pode incomodar quem preferir manter os serviços de medicamento isolados dos de usuário.
- A mesma checagem ainda não existe no `EditarMedicamentoService` (ADR-0017) — um medicamento existente ainda pode, em teoria, ter seu `idosoId` alterado para um valor inválido através da edição, já que essa validação só está no cadastro.

## Impactos

Afeta `application/service/RegistrarMedicamentoService` (nova dependência e validação) e `config/AppConfig` (ajuste na montagem, passando `usuarioCsvAdapter`).

## Implementação

1. Adicionar `SalvarUsuarioPort` como dependência do construtor de `RegistrarMedicamentoService`.
2. Antes de criar o `Medicamento`, buscar o `idosoId` via `buscarPorId`.
3. Lançar `IllegalArgumentException` se a busca vier vazia (id não existe).
4. Lançar `IllegalArgumentException` se o resultado não for uma instância de `Idoso`.
5. Atualizar o `AppConfig` para passar `usuarioCsvAdapter` na montagem do serviço.
6. Testar cadastrando com um id de idoso válido, um id inexistente, e um id de familiar, conferindo os três comportamentos.

## Observações

A mesma validação ainda precisa ser estendida ao `EditarMedicamentoService` (ADR-0017), que hoje permite alterar o medicamento sem revalidar o `idosoId` — pendência a ser resolvida numa próxima iteração.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro