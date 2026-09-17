# ADR-0037 — Login com mensagem genérica para credenciais inválidas

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** O sistema precisa autenticar um usuário (idoso ou familiar) a partir de email e senha, sem nunca ter guardado a senha original (ADR-0028) — a verificação precisa comparar a senha digitada contra o hash salvo.
- **Por que esse problema é importante?** A forma como os erros de login são comunicados tem implicação direta de segurança: se o sistema disser "email não encontrado" separado de "senha incorreta", alguém malicioso consegue descobrir quais emails têm conta cadastrada no sistema só tentando login um por um (enumeração de usuários).
- **Limitações e requisitos envolvidos:** a verificação de senha precisa usar `CriptografarSenhaPort.verificarSenha` (ADR-0028), nunca comparação direta de texto contra hash.
- **Situação atual a modificar:** não existia nenhum caso de uso de login antes desta decisão.

## Decisão

Criar `RealizarLoginCase`/`RealizarLoginService`, que busca o usuário por email (`SalvarUsuarioPort.buscarPorEmail`) e verifica a senha (`CriptografarSenhaPort.verificarSenha`), lançando a mesma exceção (`CredenciaisInvalidasException`, com a mesma mensagem) tanto quando o email não existe quanto quando a senha está incorreta.

- **O que será utilizado ou alterado?** Nova interface `RealizarLoginCase`, nova classe `RealizarLoginService`, nova exceção `CredenciaisInvalidasException`.
- **Como a solução será aplicada?** Dois pontos de falha possíveis (`usuario == null` e senha não confere) levam à mesma exceção, sem nenhuma distinção visível para quem chama o serviço.
- **Por que essa alternativa foi escolhida?** É a prática recomendada de segurança para autenticação — nunca revelar se a causa da falha foi o identificador ou a credencial.

## Alternativas consideradas

### Alternativa 1 — Mensagem genérica única para as duas causas de falha

**Vantagens:**
- Impede enumeração de usuários cadastrados via tentativa de login.
- Segue prática de segurança amplamente recomendada para sistemas de autenticação.

**Desvantagens:**
- Uma futura interface não consegue orientar o usuário de forma mais específica ("verifique seu email" vs. "senha incorreta") sem reintroduzir o problema de segurança.

### Alternativa 2 — Mensagens distintas para "email não encontrado" e "senha incorreta"

**Vantagens:**
- Feedback mais específico e amigável para quem está tentando logar legitimamente e errou algum dado.

**Desvantagens:**
- Permite enumeração de usuários — um atacante descobre quais emails têm conta cadastrada testando um por um.

### Alternativa 3 — Bloqueio temporário após tentativas repetidas, mantendo mensagens distintas

Adicionar um mecanismo de limitação de tentativas, aceitando mensagens específicas por já ter essa proteção adicional.

**Vantagens:**
- Mitigaria parte do risco de enumeração em massa, mesmo com mensagens específicas.

**Desvantagens:**
- Não elimina o risco, só o torna mais lento — a enumeração ainda seria possível, apenas mais demorada.
- Exige um mecanismo de controle de tentativas (contador, tempo de espera) que o projeto ainda não tem nenhuma base para suportar.

## Justificativa

- **Segurança:** elimina por completo o vetor de enumeração de usuários via mensagens de erro diferenciadas — a proteção mais simples e eficaz para esse risco específico.
- **Compatibilidade com o projeto:** reaproveita diretamente as portas já existentes (`buscarPorEmail`, `verificarSenha`), sem exigir nada novo além da orquestração.
- **Simplicidade:** uma única exceção, sem parâmetros que diferenciem a causa, é a implementação mais direta da decisão.

## Consequências

**Positivas**
- Impossível descobrir quais emails têm conta cadastrada apenas testando logins.
- `RealizarLoginService` fica pequeno e direto, com toda a lógica de autenticação num único lugar.

**Negativas**
- O feedback ao usuário legítimo que errou a senha é menos específico do que poderia ser — ele só sabe que "algo" está errado, não o quê.

## Impactos

Afeta `domain/exception/CredenciaisInvalidasException` (nova), `domain/port/in/RealizarLoginCase` (nova) e `application/service/RealizarLoginService` (nova), além de `config/AppConfig` (novo método de montagem).

## Implementação

1. Criar `CredenciaisInvalidasException`, com mensagem fixa ("Email ou senha inválidos.").
2. Criar `RealizarLoginCase` e `RealizarLoginService`, usando `buscarPorEmail` e `verificarSenha`.
3. Adicionar `criarRealizarLoginService()` ao `AppConfig`.
4. Testar login com email inexistente e com senha errada, confirmando que a mensagem de erro é idêntica nos dois casos.

## Observações

Se o projeto evoluir para uma API pública exposta na internet, vale complementar esta proteção com limitação de tentativas (rate limiting), já que a mensagem genérica sozinha não impede tentativas de força bruta, só a enumeração de usuários.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro