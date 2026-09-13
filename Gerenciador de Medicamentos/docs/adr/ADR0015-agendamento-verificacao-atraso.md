# ADR-0015 — Agendamento automático da verificação de atraso (execução a cada minuto)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** A verificação de atraso (ADR-0014) só rodava quando chamada manualmente — o sistema precisa rodar essa checagem sozinho, continuamente, já que o CuidaMed é pensado para ficar aberto o tempo todo.
- **Por que esse problema é importante?** Sem execução automática, o sistema depende de alguém lembrar de chamar a verificação — o que contraria o propósito do CuidaMed de lembrar as pessoas sem depender de intervenção manual constante.
- **Limitações e requisitos envolvidos:** a verificação precisa rodar a cada minuto, sem travar o restante do programa, e sem que uma falha pontual (ex: erro de leitura de um arquivo) derrube o agendamento inteiro.
- **Situação atual a modificar:** o `Main` chamava `verificarAtrasoMedicamentoService.verificarAtrasos()` manualmente, uma única vez, dentro do cenário de teste.

## Decisão

Criar o `AgendadorVerificacaoAtraso`, um adapter de entrada que usa `ScheduledExecutorService` para chamar `verificarAtrasos()` automaticamente, a cada 1 minuto, capturando qualquer exceção internamente para não interromper o agendamento.

- **O que será utilizado ou alterado?** Nova classe `AgendadorVerificacaoAtraso` em `adapter/in/scheduler/`, usando a API de concorrência padrão do Java (`java.util.concurrent`).
- **Como a solução será aplicada?** `scheduleAtFixedRate` chama `verificarAtrasos()` a cada 60 segundos, com um `try/catch` interno para que uma exceção pontual não interrompa as próximas execuções.
- **Por que essa alternativa foi escolhida?** É a solução nativa do Java para agendamento periódico, sem exigir bibliotecas externas, adequada ao porte atual do projeto.

## Alternativas consideradas

### Alternativa 1 — `ScheduledExecutorService` rodando a cada minuto, com tratamento de erro interno

**Vantagens:**
- Recurso nativo do Java, sem dependência externa.
- O `try/catch` interno protege o agendamento de ser interrompido por um erro pontual.

**Desvantagens:**
- Reagenda a checagem completa a cada minuto, mesmo quando nada mudou desde a última verificação.

### Alternativa 2 — Thread própria com `Thread.sleep(60000)` em loop

Uma thread manual que dorme por um minuto e executa a verificação em loop infinito.

**Vantagens:**
- Simples de entender, sem usar a API de `Executor`.

**Desvantagens:**
- Mais propenso a erro de implementação (tratamento de interrupção, encerramento da thread) do que usar a API pronta do Java para isso.
- Uma exceção não tratada dentro do loop encerra a thread inteira, sem chance de recuperação automática.

### Alternativa 3 — Ferramenta externa de agendamento (ex: cron do sistema operacional chamando o programa)

Delegar o agendamento para o sistema operacional, chamando o programa periodicamente via cron ou Task Scheduler.

**Vantagens:**
- Desacopla o agendamento do próprio código Java.

**Desvantagens:**
- Cada execução seria um processo novo, sem estado compartilhado entre chamadas, complicando cenários como o agendador rodar continuamente junto com outras partes do sistema.
- Menos portável entre sistemas operacionais diferentes.

## Justificativa

- **Compatibilidade com o projeto:** `ScheduledExecutorService` é parte do Java padrão, sem exigir bibliotecas externas nem infraestrutura de sistema operacional.
- **Manutenibilidade:** a lógica de agendamento fica isolada numa única classe, separada da regra de negócio de verificação em si (que continua no `VerificarAtrasoMedicamentoService`).
- **Segurança:** o `try/catch` interno evita que uma falha pontual (ex: um CSV temporariamente ilegível) pare o agendamento silenciosamente.

## Consequências

**Positivas**
- O sistema passa a verificar atrasos sozinho, sem depender de chamada manual.
- Uma falha numa execução não compromete as verificações seguintes, graças ao tratamento de erro interno.

**Negativas**
- O programa deixa de encerrar sozinho ao final do `main()` — a thread do agendador mantém o processo rodando indefinidamente, o que é intencional, mas é uma mudança de comportamento que precisa ser conhecida por quem for rodar ou depurar o sistema.
- A cada minuto, os arquivos de usuários, medicamentos e histórico são relidos por completo — aceitável para o volume atual, mas um ponto de atenção se a base de dados crescer.

## Impactos

Afeta `adapter/in/scheduler/AgendadorVerificacaoAtraso` (novo) e o `Main`, que passa a iniciar o agendador ao final do cenário de teste.

## Implementação

1. Criar `AgendadorVerificacaoAtraso`, recebendo o `VerificarAtrasoMedicamentoCase` no construtor.
2. Implementar `iniciar()`, agendando a chamada a cada 1 minuto via `scheduleAtFixedRate`, com `try/catch` interno.
3. Implementar `parar()`, para encerrar o agendamento quando necessário (por exemplo, futuramente, via um comando de desligar o sistema).
4. Chamar `agendador.iniciar()` no `Main`, após os testes manuais.
5. Validar rodando o programa por alguns minutos e conferindo que a verificação dispara automaticamente no console.

## Observações

Releitura completa dos arquivos CSV a cada minuto é aceitável para o volume de dados atual do projeto, mas deve ser revisitada (por exemplo, cacheando dados em memória entre execuções, ou migrando para um banco de dados) se a base crescer significativamente — ponto já sinalizado na ADR-0014.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro