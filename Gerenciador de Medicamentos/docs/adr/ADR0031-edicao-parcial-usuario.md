# ADR-0031 — 

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `EditarUsuarioCase.editarUsuario` exigia sempre os três campos (`nome`, `email`, `senha`) preenchidos, mesmo quando a intenção era mudar só um deles — editar apenas o nome, por exemplo, ainda exigia reenviar o email e a senha atuais.
- **Por que esse problema é importante?** Forçar o reenvio de todos os campos numa edição é inconveniente e propenso a erro (por exemplo, reenviar um email desatualizado por engano, sobrescrevendo um valor correto com um valor antigo que o formulário ainda carregava).
- **Limitações e requisitos envolvidos:** os campos que não forem alterados não devem disparar validação (um `email` não enviado não deveria falhar por "formato inválido", por exemplo) nem sobrescrever o valor já salvo.
- **Situação atual a modificar:** `ValidarDadosUsuario.validarUsuario(nome, email, senha)` validava os três campos de uma vez, sempre como obrigatórios; `EditarUsuarioService` sempre chamava `setNome`/`setEmail`/`setSenha` para os três, incondicionalmente.

## Decisão

Tratar `null` em qualquer um dos três parâmetros de `editarUsuario` como "não alterar este campo" — pulando tanto a validação quanto a atualização daquele campo específico. Refatorar `ValidarDadosUsuario` para expor validações individuais por campo (`validarNome`, `validarEmail`, `validarSenha`), além do `validarUsuario` existente (mantido para o cadastro, que continua exigindo os três).

- **O que será utilizado ou alterado?** `ValidarDadosUsuario` ganha três métodos novos, sem remover o `validarUsuario` existente; `EditarUsuarioService.editarUsuario` passa a checar `!= null` em cada campo antes de validar e alterar.
- **Como a solução será aplicada?** Cada campo é tratado num bloco independente: `if (campo != null) { validar; alterar; }`. Uma string vazia (`""`) continua sendo erro de validação — só `null` significa "não alterar".
- **Por que essa alternativa foi escolhida?** É o padrão mais simples de implementar sem introduzir um tipo novo (como um objeto de "patch" ou `Optional` por campo), reaproveitando o próprio `null` do Java como sinalizador, já que os três parâmetros são tipos de referência (`String`).

## Alternativas consideradas

### Alternativa 1 — `null` como sinalizador de "não alterar", validação por campo

**Vantagens:**
- Simples de implementar e de entender: cada campo é independente.
- Não exige nenhum tipo novo além dos já usados (`String`).

**Desvantagens:**
- Depende de quem chama o método saber e lembrar da convenção "`null` = não mudar" — se alguém confundir com "definir como vazio", o comportamento não é o esperado (lança erro de validação, não limpa o campo).

### Alternativa 2 — Objeto de "patch" dedicado, com campos opcionais explícitos (ex: `Optional<String>` por campo, ou um DTO com flags de "foi informado")

**Vantagens:**
- Mais explícito sobre a intenção de "campo não informado" vs. "campo definido como vazio", sem depender de uma convenção implícita sobre `null`.

**Desvantagens:**
- Adiciona uma classe nova e mais complexidade de construção só para expressar uma diferenciação que, no caso deste projeto, os próprios campos (nome, email, senha) nunca fazem sentido como "vazio de propósito" — a Alternativa 1 já cobre o caso real sem esse custo extra.

### Alternativa 3 — Métodos separados por campo (`editarNome`, `editarEmail`, `editarSenha`), sem um único `editarUsuario`

Ter uma operação de edição por campo, cada uma explícita sobre o que altera.

**Vantagens:**
- Sem ambiguidade nenhuma sobre o que cada chamada faz — o nome do método já diz.

**Desvantagens:**
- Contraria diretamente a decisão da ADR-0020 (unificar a edição num único método) — reintroduziria a fragmentação que aquela ADR eliminou, sem o motivo que a justificava (lá, o problema era o cast por tipo de usuário, não o número de campos).
- Editar mais de um campo ao mesmo tempo exigiria múltiplas chamadas sequenciais, cada uma lendo e reescrevendo o arquivo CSV inteiro (ADR-0009) — pior para desempenho do que uma única chamada com todos os campos desejados.

## Justificativa

- **Facilidade de desenvolvimento:** editar um único campo não exige mais conhecer e reenviar o valor atual dos outros dois.
- **Compatibilidade com o projeto:** mantém a unificação de `editarUsuario` da ADR-0020, sem reintroduzir a fragmentação por tipo/campo que aquela decisão eliminou.
- **Manutenibilidade:** a refatoração de `ValidarDadosUsuario` em métodos menores é reaproveitada tanto pelo cadastro (via `validarUsuario`, que chama os três) quanto pela edição parcial (que chama só o que for relevante), sem duplicar a lógica de validação em dois lugares.
- **Simplicidade:** usa o próprio `null` da linguagem, sem introduzir um tipo novo para representar "campo não informado" — adequado ao porte atual do projeto.

## Consequências

**Positivas**
- Dá para editar qualquer combinação de nome, email e senha numa única chamada, sem precisar reenviar os campos que não mudam.
- A validação de dados continua acontecendo normalmente para os campos que de fato são alterados.

**Negativas**
- Depende de uma convenção implícita (`null` = não alterar) que precisa ser documentada e lembrada por quem for usar `editarUsuario` — um erro comum seria confundir isso com "definir como vazio".
- `ValidarDadosUsuario` agora tem quatro métodos públicos em vez de um, exigindo que quem for usá-la escolha entre o método completo (`validarUsuario`) e os individuais, dependendo do contexto (cadastro vs. edição parcial).

## Impactos

Afeta `domain/validation/ValidarDadosUsuario` (novos métodos `validarNome`, `validarEmail`, `validarSenha`) e `application/service/EditarUsuarioService` (lógica condicional por campo).

## Implementação

1. Refatorar `ValidarDadosUsuario`, extraindo `validarNome`, `validarEmail` e `validarSenha` como métodos próprios, reaproveitados pelo `validarUsuario` existente.
2. Em `EditarUsuarioService.editarUsuario`, envolver cada campo (`nome`, `email`, `senha`) num bloco `if (campo != null) { ... }`, chamando a validação individual correspondente antes de alterar.
3. Manter a checagem de email duplicado (ADR-0029) e a checagem de senha inalterada (ADR-0030) dentro do bloco condicional do respectivo campo.
4. Testar editando cada campo isoladamente, todos juntos, e nenhum (todos `null`) — o último caso deve persistir o usuário sem nenhuma alteração real.

## Observações

A convenção "`null` = não alterar" deve ser documentada no Javadoc de `EditarUsuarioCase.editarUsuario`, para que qualquer novo ponto de entrada (uma futura API REST, por exemplo) saiba interpretar campos ausentes corretamente como "manter valor atual", e não como "limpar o campo".

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro