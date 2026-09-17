# ADR-0041 — Painel de medicamentos compartilhado entre idoso e familiar

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Tanto o idoso quanto o familiar que o acompanha precisam poder ver, cadastrar e editar os medicamentos daquele idoso — a operação em si é idêntica nos dois casos, só muda quem está realizando a ação.
- **Por que esse problema é importante?** Sem uma peça compartilhada, a lógica de listar/cadastrar/editar medicamento precisaria ser escrita (e mantida) duas vezes — uma dentro de `TelaIdoso`, outra dentro de `TelaFamiliar` — violando o princípio de responsabilidade única e criando risco de as duas implementações divergirem ao longo do tempo.
- **Limitações e requisitos envolvidos:** a operação de cadastro/edição já valida, na camada de aplicação, que o `idosoId` informado corresponde a um `Idoso` de verdade (ADR-0018/0033) — essa proteção precisa continuar valendo independentemente de quem (idoso ou familiar) estiver operando a tela.
- **Situação atual a modificar:** `TelaIdoso` continha sua própria lógica de cadastro e listagem de medicamentos; `TelaFamiliar` só listava (sem cadastrar/editar), sem reaproveitar nada de `TelaIdoso`.

## Decisão

Extrair a listagem, cadastro e edição de medicamentos para uma classe própria, `PainelMedicamentos`, parametrizada pelo `Idoso` alvo (não por quem está logado), reaproveitada tanto por `TelaIdoso` (passando o próprio idoso logado) quanto por `TelaFamiliar` (passando o idoso que o familiar selecionou entre os que acompanha).

- **O que será utilizado ou alterado?** Nova classe `PainelMedicamentos` em `adapter/in/console/`; `TelaIdoso` simplificada, delegando a esse painel; `TelaFamiliar` passa a abrir o mesmo painel ao selecionar um idoso, em vez de uma listagem própria e mais limitada.
- **Como a solução será aplicada?** `PainelMedicamentos` recebe o `Idoso` no construtor e opera sempre em cima dele — não sabe (nem precisa saber) se quem está no terminal é o próprio idoso ou um familiar.
- **Por que essa alternativa foi escolhida?** A operação "gerenciar medicamentos de um idoso" é a mesma independentemente de quem a realiza — parametrizar pelo idoso alvo, em vez de duplicar a lógica por tipo de usuário logado, é a aplicação direta do princípio de responsabilidade única (mesmo raciocínio já usado na unificação de edição de usuário, ADR-0020).

## Alternativas consideradas

### Alternativa 1 — Classe única `PainelMedicamentos`, parametrizada pelo idoso alvo

**Vantagens:**
- Uma única implementação para manter; qualquer melhoria futura (mais um filtro, um novo campo) beneficia automaticamente as duas telas que a chamam.
- Reduz risco de `TelaIdoso` e `TelaFamiliar` divergirem silenciosamente em como tratam o mesmo tipo de dado.

**Desvantagens:**
- Se um dia idoso e familiar precisarem de permissões diferentes sobre o mesmo painel (por exemplo, só o idoso pode excluir um medicamento), a classe única precisaria de alguma forma de diferenciar esse caso — não é um problema hoje, porque as permissões atuais são idênticas para os dois.

### Alternativa 2 — Lógica duplicada em `TelaIdoso` e `TelaFamiliar`, cada uma com sua própria implementação

**Vantagens:**
- Cada tela evolui de forma independente, sem risco de uma mudança pensada para um caso afetar o outro sem querer.

**Desvantagens:**
- Duplicação direta de código para uma operação que hoje é idêntica nos dois contextos — qualquer correção de bug ou melhoria precisaria ser replicada manualmente nos dois lugares.

### Alternativa 3 — Painel compartilhado, mas recebendo o `Usuario` logado (idoso ou familiar) em vez do idoso alvo diretamente

Passar quem está operando o painel, deixando a classe decidir internamente qual idoso está em jogo.

**Vantagens:**
- Painel teria acesso a mais contexto sobre quem o está usando, caso isso um dia seja necessário para uma regra de permissão diferenciada.

**Desvantagens:**
- Complica a classe sem necessidade real hoje — ela precisaria de lógica extra só para "descobrir" qual idoso está em jogo quando quem chama é um familiar (múltiplos idosos possíveis) versus quando é o próprio idoso (só ele mesmo). Receber o idoso alvo já resolvido, calculado por quem chama, é mais simples e mantém a classe focada em uma coisa só.

## Justificativa

- **Manutenibilidade:** uma única implementação de "gerenciar medicamentos de um idoso" para manter, testar e evoluir.
- **Compatibilidade com o projeto:** segue o mesmo raciocínio já aplicado na unificação da edição de usuário (ADR-0020) — operações idênticas não deveriam ser fragmentadas por "quem" as realiza, quando a diferença não afeta o comportamento da operação em si.
- **Facilidade de desenvolvimento:** simplifica `TelaIdoso`, que passa a ter uma única responsabilidade (mostrar notificações e abrir o painel), e dá a `TelaFamiliar` a mesma capacidade completa (cadastrar/editar, não só ver) sem esforço adicional de implementação.

## Consequências

**Positivas**
- Familiar ganha a capacidade de cadastrar e editar medicamentos do idoso que acompanha, não só visualizar — atendendo diretamente ao requisito descrito para o terminal interativo.
- Uma única fonte de verdade para a lógica de gerenciamento de medicamento no terminal, reduzindo risco de divergência entre as duas telas que a utilizam.

**Negativas**
- Caso surja, no futuro, uma regra de permissão que diferencie o que idoso e familiar podem fazer sobre o mesmo painel (por exemplo, restringir exclusão só ao idoso), a classe única precisará ser adaptada para saber "quem" a está operando, não só "sobre quem" ela opera.

## Impactos

Afeta `adapter/in/console/PainelMedicamentos` (nova classe), `adapter/in/console/TelaIdoso` (simplificada) e `adapter/in/console/TelaFamiliar` (passa a abrir o painel completo em vez de uma listagem própria).

## Implementação

1. Criar `PainelMedicamentos`, recebendo `Scanner` e `Idoso` no construtor, com as operações listar/cadastrar/editar já existentes nas telas anteriores.
2. Simplificar `TelaIdoso.exibir()` para mostrar notificações e delegar ao painel, passando o próprio idoso logado.
3. Ajustar `TelaFamiliar` para abrir `PainelMedicamentos` (em vez do método de listagem antigo) ao selecionar um idoso da lista de acompanhados.
4. Testar: idoso cadastrando/editando os próprios medicamentos; familiar cadastrando/editando medicamentos de um idoso que acompanha; confirmar que a validação de dono (ADR-0018/0033) continua rejeitando tentativas inválidas nos dois casos.

## Observações

A opção de edição no terminal usa a convenção de edição parcial já estabelecida (campo deixado em branco = não alterar, ADR-0031), traduzindo a entrada vazia do usuário para `null` antes de repassar ao `EditarMedicamentoService`.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro