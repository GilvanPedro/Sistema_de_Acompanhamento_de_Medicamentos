# ADR-0028 — Hash de senha com BCrypt no cadastro e edição de usuário

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** As senhas de `Idoso` e `Familiar` eram salvas em texto puro em `usuarios.csv` — qualquer pessoa com acesso ao arquivo veria a senha real de qualquer usuário.
- **Por que esse problema é importante?** Senha em texto puro é uma falha de segurança básica. O sistema não precisa (e não deveria) conseguir recuperar a senha original em nenhum momento — só precisa confirmar, num futuro login, se a senha digitada corresponde à cadastrada.
- **Limitações e requisitos envolvidos:** a verificação de senha (login) precisa funcionar sem que o sistema jamais tenha acesso à senha original em texto puro após o cadastro — apenas à sua forma protegida.
- **Situação atual a modificar:** `RegistrarUsuarioService` e `EditarUsuarioService` recebiam a senha em texto puro e a persistiam sem nenhuma proteção.

## Decisão

Adotar hash de senha (não criptografia reversível) usando BCrypt, através de uma porta `CriptografarSenhaPort` implementada por `BcryptSenhaAdapter`, aplicada no cadastro (`RegistrarUsuarioService`) e na edição (`EditarUsuarioService`) antes de qualquer persistência.

- **O que será utilizado ou alterado?** Nova dependência `jbcrypt` no `pom.xml`; nova porta `CriptografarSenhaPort` em `domain/port/out`; novo adapter `BcryptSenhaAdapter` em `adapter/out/security`; `RegistrarUsuarioService` e `EditarUsuarioService` passam a hashear a senha antes de criar/atualizar o objeto de domínio.
- **Como a solução será aplicada?** `criptografar(senha)` gera um hash com sal aleatório (`BCrypt.hashpw` com `gensalt(12)`); `verificar(senhaDigitada, senhaArmazenada)` compara a senha digitada contra o hash salvo, usada futuramente no login.
- **Por que essa alternativa foi escolhida?** BCrypt é o padrão consolidado para hash de senha — gera automaticamente um sal diferente por senha (evitando que senhas iguais gerem o mesmo hash) e permite ajustar o custo computacional (dificultando ataques de força bruta), sem exigir gerenciar chaves de criptografia.

## Alternativas consideradas

### Alternativa 1 — Hash com BCrypt (sem possibilidade de reverter)

**Vantagens:**
- Nunca é possível recuperar a senha original, nem mesmo por quem administra o sistema — se o `usuarios.csv` vazar, as senhas continuam protegidas.
- Sal automático por senha, custo computacional ajustável, biblioteca amplamente testada e usada no mercado.

**Desvantagens:**
- Introduz uma dependência externa nova (`jbcrypt`) ao projeto, que até então só tinha a `caelum-stella-core`.

### Alternativa 2 — Criptografia simétrica reversível (ex: AES), permitindo decifrar a senha

**Vantagens:**
- Permitiria, em teoria, recuperar a senha original se necessário.

**Desvantagens:**
- Contraria o próprio requisito — o pedido era que "o sistema não soubesse a senha", e criptografia reversível significa que, com a chave certa, o sistema (ou quem tiver acesso a ela) sempre pode saber. Hash, não criptografia, é o que resolve esse problema corretamente.
- Exigiria gerenciar e proteger uma chave de criptografia, um problema de segurança adicional que o hash não tem.

### Alternativa 3 — Hash simples sem sal (ex: SHA-256 puro, sem BCrypt)

**Vantagens:**
- Mais simples de implementar, sem dependência externa (SHA-256 já vem no Java padrão).

**Desvantagens:**
- Sem sal, senhas iguais geram hashes idênticos — visível a quem tiver acesso ao arquivo.
- Hashes rápidos como SHA-256 são vulneráveis a ataques de força bruta com tabelas pré-computadas (rainbow tables) e hardware especializado, justamente o que o custo ajustável do BCrypt dificulta.

## Justificativa

- **Segurança:** BCrypt segue as práticas recomendadas para armazenamento de senha — nunca reversível, sal automático, custo ajustável.
- **Compatibilidade com o projeto:** implementado como porta e adapter, seguindo a Arquitetura Hexagonal já adotada (ADR-0001) — o domínio não sabe que a implementação é BCrypt, só que existe um contrato de proteger e verificar senha.
- **Facilidade de desenvolvimento:** biblioteca pronta e amplamente documentada, evitando implementar lógica de hash e sal na mão.

## Consequências

**Positivas**
- Nenhuma senha é mais persistida em texto puro, no cadastro nem na edição.
- A porta `verificar` já deixa o sistema pronto para implementar login no futuro, sem precisar de nenhuma mudança na forma como a senha é armazenada.

**Negativas**
- Os dados de teste já persistidos em `usuarios.csv` (senhas em texto puro) tornam-se incompatíveis com o novo formato — precisam ser apagados e recriados, já que `BCrypt.checkpw` espera um hash válido no segundo parâmetro.
- Nova dependência externa (`jbcrypt`) adicionada ao projeto.

## Impactos

Afeta `pom.xml` (nova dependência), `domain/port/out/CriptografarSenhaPort` (novo), `adapter/out/security/BcryptSenhaAdapter` (novo), `application/service/RegistrarUsuarioService` e `application/service/EditarUsuarioService` (nova dependência e chamada de hash), e `config/AppConfig` (nova instância compartilhada e ajuste nos métodos de montagem desses dois serviços).

## Implementação

1. Adicionar a dependência `jbcrypt` ao `pom.xml`.
2. Criar `CriptografarSenhaPort` em `domain/port/out`.
3. Implementar `BcryptSenhaAdapter` em `adapter/out/security`.
4. Injetar `CriptografarSenhaPort` em `RegistrarUsuarioService` e `EditarUsuarioService`, hasheando a senha antes de criar/atualizar o objeto.
5. Atualizar `AppConfig` com a instância compartilhada de `CriptografarSenhaPort` e ajustar os métodos de montagem afetados.
6. Apagar os dados de teste existentes em `usuarios.csv` (senhas em texto puro, incompatíveis com o novo formato) e recadastrar.
7. Testar cadastrando um usuário, conferindo que o hash salvo no CSV não corresponde à senha em texto puro, e validando `verificar()` com a senha correta e uma incorreta.

## Observações

O método `verificar()` da porta ainda não é consumido por nenhum código — está pronto para o futuro fluxo de login, que ainda não foi implementado. Vale revisitar esta ADR quando o login for de fato construído, para confirmar que o uso real bate com o contrato definido aqui.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro