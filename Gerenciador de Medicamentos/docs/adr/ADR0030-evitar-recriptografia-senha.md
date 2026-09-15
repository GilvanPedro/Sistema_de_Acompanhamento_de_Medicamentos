# ADR-0030 — Evitar recriptografar a senha quando ela não mudou

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Ao editar um usuário, a senha recebida (em texto puro) sempre era recriptografada e salva, mesmo quando o valor era exatamente a mesma senha de antes.
- **Por que esse problema é importante?** BCrypt (ADR-0028) é proposital e computacionalmente custoso, para dificultar força bruta — gerar um novo hash a cada edição, mesmo sem mudança real de senha, desperdiça esse custo sem necessidade.
- **Limitações e requisitos envolvidos:** não é possível comparar a senha recebida (texto puro) diretamente com o hash salvo usando `.equals()` — hashes de uma mesma senha são diferentes a cada geração, por causa do sal aleatório do BCrypt. A comparação precisa usar a função de verificação apropriada.
- **Situação atual a modificar:** `EditarUsuarioService` chamava `criptografarSenhaPort.criptografar(senha)` incondicionalmente, sem checar se a senha realmente mudou.

## Decisão

Usar `CriptografarSenhaPort.verificar(senhaDigitada, senhaArmazenada)` para checar se a senha recebida corresponde à já armazenada antes de decidir se um novo hash precisa ser gerado. Só chamar `criptografar` quando `verificar` indicar que a senha é diferente da atual.

- **O que será utilizado ou alterado?** `EditarUsuarioService.editarUsuario`, que passa a checar `!criptografarSenhaPort.verificar(senha, usuario.getSenha())` antes de sobrescrever a senha.
- **Como a solução será aplicada?** Se `verificar` devolver `true` (a senha recebida bate com o hash salvo), nada é alterado. Se devolver `false`, um novo hash é gerado e salvo.
- **Por que essa alternativa foi escolhida?** É o único jeito correto de comparar uma senha em texto puro com um hash BCrypt — usar a função de verificação da própria biblioteca, em vez de tentar comparar os valores diretamente.

## Alternativas consideradas

### Alternativa 1 — Usar `verificar` para checar mudança antes de recriptografar

**Vantagens:**
- Evita custo computacional desnecessário quando a senha não mudou.
- Único método que compara corretamente texto puro com hash — qualquer outra forma de comparação direta (`.equals()`) sempre falharia por causa do sal aleatório.

**Desvantagens:**
- Exige que o campo de senha continue sempre sendo enviado no formulário de edição, mesmo quando não vai mudar — sem isso, não há como saber se "não mudou" ou "o usuário só não preencheu".

### Alternativa 2 — Sempre recriptografar, sem checagem (situação anterior)

**Vantagens:**
- Código mais simples, uma linha a menos de lógica.

**Desvantagens:**
- Desperdiça o custo computacional do BCrypt em toda edição, mesmo sem mudança de senha real.

### Alternativa 3 — Tratar o campo senha como opcional (`null` = não mudar), em vez de comparar com `verificar`

Combinar essa decisão com o design de edição parcial (ADR-0031): se `senha` vier `null`, não mexe; se vier preenchida, sempre recriptografa (sem checar se é igual à antiga).

**Vantagens:**
- Mais simples de entender — não depende de comparar hash com texto puro, só de checar se o campo veio nulo.
- Evita a exigência de sempre reenviar a senha atual só para "não mudar nada".

**Desvantagens:**
- Não detecta o caso em que a pessoa preenche o campo de senha com a mesma senha de antes — nesse caso, ainda recriptografaria à toa. Só evita o desperdício quando o campo é deixado em branco/nulo, não quando é preenchido com o valor igual.

## Justificativa

- **Desempenho:** evita o custo computacional do BCrypt em edições que não envolvem troca de senha real.
- **Correção técnica:** é a única forma correta de comparar senha em texto puro com hash salvo — qualquer comparação direta de string sempre falharia por design do BCrypt (sal aleatório).
- **Compatibilidade com o projeto:** reaproveita o contrato já definido em `CriptografarSenhaPort` (ADR-0028), que já previa o método `verificar` justamente para esse tipo de checagem.

## Consequências

**Positivas**
- Senha só é recriptografada quando de fato muda, economizando processamento.
- Não há risco de comparar incorretamente hash com texto puro, já que a comparação usa a função apropriada.

**Negativas**
- Combinado com a edição parcial (ADR-0031), o comportamento de "senha não muda" passa a ter duas causas possíveis (campo nulo, ou campo preenchido mas igual ao valor atual) — o que funciona corretamente, mas é uma nuance que quem mantém o código precisa conhecer.

## Impactos

Afeta `application/service/EditarUsuarioService` (lógica de comparação antes da atualização de senha).

## Implementação

1. Em `EditarUsuarioService.editarUsuario`, antes de definir a nova senha, chamar `criptografarSenhaPort.verificar(senha, usuario.getSenha())`.
2. Só chamar `criptografarSenhaPort.criptografar(senha)` e `usuario.setSenha(...)` se a verificação indicar que a senha é diferente da atual.
3. Testar: editar mantendo a mesma senha (hash no CSV não deve mudar) e editar com senha diferente (hash deve mudar).

## Observações

Esta decisão foi tomada antes da introdução da edição parcial (ADR-0031) e continua válida dentro dela — quando o campo `senha` vem preenchido (não nulo), esta checagem de `verificar` ainda se aplica antes de decidir recriptografar.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro