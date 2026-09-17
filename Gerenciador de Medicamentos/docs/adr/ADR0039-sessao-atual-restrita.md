# ADR-0038 — `SessaoAtual` restrita ao adapter de console

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Depois do login, o sistema precisa lembrar quem está autenticado enquanto o programa roda, sem exigir que o usuário informe seu id a cada operação.
- **Por que esse problema é importante?** Onde essa "memória de quem está logado" vive tem implicação direta na evolução futura do sistema: uma solução baseada em estado estático compartilhado funciona para um programa de console (um usuário só, um processo só), mas quebra de forma perigosa se o sistema um dia virar uma API multiusuário rodando na mesma JVM.
- **Limitações e requisitos envolvidos:** o console do CuidaMed atende, por natureza, um usuário por vez, em processo único. Uma futura API (já prevista nos próximos passos do projeto) atenderia múltiplas pessoas ao mesmo tempo, na mesma instância rodando — um campo estático compartilhado nesse cenário vazaria dados de uma pessoa para outra.
- **Situação atual a modificar:** não existia nenhuma forma de guardar o usuário logado antes desta decisão.

## Decisão

Criar `SessaoAtual` como uma classe utilitária com campo estático, mas posicioná-la propositalmente em `adapter/in/console/`, não em `domain/` nem `application/`, deixando explícito pelo pacote que essa solução é específica do adapter de console e não deve ser reaproveitada por outro tipo de entrada.

- **O que será utilizado ou alterado?** Nova classe `SessaoAtual`, com métodos estáticos `login`, `logout`, `getUsuarioLogado` e `estaLogado`.
- **Como a solução será aplicada?** O `Usuario` autenticado fica guardado num campo estático dentro da classe, manipulado só pelas telas de console.
- **Por que essa alternativa foi escolhida?** Resolve a necessidade atual (console, um usuário por processo) com a implementação mais simples possível, sem introduzir infraestrutura de sessão que o projeto ainda não precisa — mas isolando essa simplicidade num pacote que sinaliza claramente sua limitação.

## Alternativas consideradas

### Alternativa 1 — Estado estático, isolado no pacote `adapter/in/console/`

**Vantagens:**
- Implementação mínima, adequada ao cenário atual (console, um usuário por vez).
- O pacote onde a classe vive já comunica, sem precisar de comentário, que essa solução não sobrevive a um cenário multiusuário.

**Desvantagens:**
- Precisa ser completamente repensada (não reaproveitada) quando uma API multiusuário for implementada.

### Alternativa 2 — Estado estático, em `domain/` ou `application/`, como se fosse parte do núcleo do sistema

**Vantagens:**
- Nenhuma vantagem real identificada.

**Desvantagens:**
- Sugeriria, pela localização, que "sessão em memória estática" é uma solução válida em qualquer contexto — inclusive numa futura API, onde causaria vazamento de dados entre usuários diferentes compartilhando a mesma instância da aplicação.
- Contraria o princípio da Arquitetura Hexagonal (ADR-0001) de manter o domínio livre de decisões específicas de uma forma de entrada.

### Alternativa 3 — Já implementar algo pensado para múltiplos usuários (token/contexto passado explicitamente), mesmo no console

Passar o `Usuario` logado como parâmetro explícito por todos os métodos, em vez de um estado implícito acessível de qualquer lugar.

**Vantagens:**
- Já nasceria compatível com um cenário multiusuário, sem precisar de reformulação futura.

**Desvantagens:**
- Complexidade desnecessária para o console atual — cada tela precisaria receber e repassar o usuário logado por parâmetro, adicionando verbosidade sem benefício real no cenário de um usuário por processo.

## Justificativa

- **Compatibilidade com o projeto:** resolve o problema real de hoje (console, um usuário) sem construir infraestrutura para um problema que ainda não existe (múltiplos usuários simultâneos).
- **Manutenibilidade:** a localização da classe (`adapter/in/console/`) funciona como documentação viva do escopo dela — quem for reaproveitar código para uma API não vai encontrar `SessaoAtual` no caminho de busca óbvio para lógica de domínio.
- **Simplicidade:** evita adicionar complexidade de contexto/token explícito antes de haver uma necessidade real que a justifique.

## Consequências

**Positivas**
- As telas de console podem consultar quem está logado a qualquer momento, sem precisar repassar o usuário por parâmetro em cada chamada.
- Fica claro, só pela localização do arquivo, que essa solução não deve ser copiada para um contexto multiusuário.

**Negativas**
- Quando uma API for implementada, essa classe não será reaproveitada — o conceito de "sessão" nesse novo contexto (token, cookie, contexto por requisição) precisará ser desenhado do zero.
- Estado estático em geral dificulta testes automatizados que rodem em paralelo, já que testes diferentes compartilhariam o mesmo estado de sessão se não forem cuidadosos em resetá-lo entre execuções.

## Impactos

Afeta `adapter/in/console/SessaoAtual` (nova classe) e as telas de console que passam a depender dela (`TelaLogin`, `MenuPrincipal`, `TelaIdoso`, `TelaFamiliar`).

## Implementação

1. Criar `SessaoAtual` em `adapter/in/console/`, com campo estático `Usuario usuarioLogado` e métodos `login`, `logout`, `getUsuarioLogado`, `estaLogado`.
2. Construtor privado, impedindo instanciação da classe.
3. `TelaLogin` chama `SessaoAtual.login(usuario)` após autenticação bem-sucedida.
4. `TerminalApp` chama `SessaoAtual.logout()` ao final de cada ciclo de uso, antes de voltar à tela de login.

## Observações

Quando uma API REST for implementada (item já presente nos próximos passos do projeto), esta ADR deve ser revisitada — o padrão de sessão nesse novo contexto não deve reaproveitar `SessaoAtual`, e sim um mecanismo compatível com múltiplas requisições concorrentes (token JWT, sessão HTTP por request, etc.).

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro