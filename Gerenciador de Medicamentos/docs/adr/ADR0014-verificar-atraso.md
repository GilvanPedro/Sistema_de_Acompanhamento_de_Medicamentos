# ADR-0014 — Verificação de atraso na tomada de medicamento

## Status

Parcialmente substituído: o `VerificarAtrasoMedicamentoService` foi removido ([ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md)); a regra de atraso continua em `VerificarNotificacoesIdosoService` e o aviso ao familiar agora é enviado pelo servidor ([ADR-0051](ADR0051-aviso-de-remedio-esquecido-por-agendador-externo.md))

## Contexto

- **Qual problema precisa ser resolvido?** O sistema precisa identificar quando um medicamento passou do horário previsto e ainda não foi confirmado como tomado, para então notificar o idoso (lembrete) e os familiares (aviso de esquecimento).
- **Por que esse problema é importante?** É o propósito central do CuidaMed — sem essa verificação, o sistema só reage a tomadas já confirmadas manualmente, não identifica esquecimentos por conta própria.
- **Limitações e requisitos envolvidos:** a tolerância de atraso definida foi de 10 minutos após o horário previsto. A verificação só deve considerar medicamentos cujo dia da semana agendado seja o dia atual. Um medicamento já confirmado como tomado no dia não deve gerar notificação de atraso novamente, só voltando a ser verificado no próximo dia da semana agendado.
- **Situação atual a modificar:** não existia nenhuma lógica que cruzasse o horário previsto de um medicamento com o histórico de tomadas para decidir se um aviso de atraso deveria ser disparado.

## Decisão

Criar o `VerificarAtrasoMedicamentoService`, que percorre todos os medicamentos, filtra os agendados para o dia da semana atual, calcula se o horário previsto mais a tolerância de 10 minutos já passou, verifica se já existe uma tomada confirmada para aquele medicamento e idoso no dia de hoje, e, se não houver, dispara o lembrete e o aviso de esquecimento.

- **O que será utilizado ou alterado?** Nova interface `VerificarAtrasoMedicamentoCase` e nova classe `VerificarAtrasoMedicamentoService`, dependendo de `SalvarUsuarioPort`, `SalvarMedicamentoPort`, `SalvarHistoricoPort` e `NotificarPort`.
- **Como a solução será aplicada?** Para cada medicamento do dia, monta-se o horário previsto (`LocalDateTime.of(hoje, medicamento.getHorarioMedicamento())`), compara-se com o momento atual usando `Duration.between(...).toMinutes()`, e cruza-se com o histórico do dia para decidir se a notificação deve disparar.
- **Por que essa alternativa foi escolhida?** Reaproveita os dados já persistidos (medicamentos, histórico) sem exigir nenhuma estrutura de dados nova, calculando o atraso sob demanda a cada verificação.

## Alternativas consideradas

### Alternativa 1 — Verificação sob demanda, recalculando o atraso a cada chamada

**Vantagens:**
- Simples, sem necessidade de guardar estado adicional sobre "o que já foi verificado".
- Sempre reflete o estado real e atual dos dados persistidos.

**Desvantagens:**
- Relê os arquivos de usuários, medicamentos e histórico inteiros a cada chamada, o que não escala bem para uma base de dados grande.

### Alternativa 2 — Guardar o estado de "já notificado" em memória ou em arquivo

Manter um registro separado de quais medicamentos já geraram aviso de atraso hoje, evitando recalcular a cada verificação.

**Vantagens:**
- Evita reprocessar toda a lógica de comparação de horário a cada chamada.

**Desvantagens:**
- Introduz mais um estado para manter sincronizado, com risco de ficar desatualizado (por exemplo, se o programa for reiniciado no meio do dia).

### Alternativa 3 — Verificação baseada em eventos disparados pelo próprio agendamento dos medicamentos

Agendar uma verificação específica para cada horário de medicamento, em vez de uma varredura geral.

**Vantagens:**
- Mais eficiente, checando só no momento exato em que um medicamento pode estar atrasado.

**Desvantagens:**
- Exige um mecanismo de agendamento por evento (um "alarme" por medicamento) bem mais complexo do que uma verificação periódica simples.

## Justificativa

- **Compatibilidade com o projeto:** aproveita a estrutura de portas já existente (usuário, medicamento, histórico, notificação), sem introduzir tecnologia nova.
- **Facilidade de desenvolvimento:** a lógica de comparação de horários e checagem de histórico já tomado é direta de implementar com as classes de data e hora do Java (`LocalDateTime`, `Duration`).
- **Manutenibilidade:** a tolerância de atraso (10 minutos) fica isolada numa constante, fácil de ajustar depois.
- **Custo:** aceitável reler os dados a cada verificação, dado o volume pequeno de dados do projeto nesta fase.

## Consequências

**Positivas**
- O sistema passa a identificar sozinho quando um medicamento não foi tomado no prazo, sem depender de alguém checar manualmente.
- Evita notificações repetidas para um medicamento já confirmado como tomado no dia.

**Negativas**
- Releitura completa dos três arquivos CSV a cada verificação — aceitável agora, mas pode pesar se o volume de dados crescer (ver observações da ADR-0015).
- A tolerância fixa de 10 minutos está fixada em código; não é configurável por medicamento ou por idoso.

## Impactos

Afeta `domain/port/in/VerificarAtrasoMedicamentoCase` (novo) e `application/service/VerificarAtrasoMedicamentoService` (novo), consumindo `SalvarUsuarioPort`, `SalvarMedicamentoPort`, `SalvarHistoricoPort` e `NotificarPort`.

## Implementação

1. Criar a interface `VerificarAtrasoMedicamentoCase`.
2. Implementar `VerificarAtrasoMedicamentoService`, montando os mapas de idosos e medicamentos a partir das portas existentes.
3. Implementar o filtro por dia da semana, o cálculo de atraso e a checagem de "já tomado hoje".
4. Disparar `lembrarIdoso` e `avisarRemedioEsquecido` quando o atraso for confirmado.
5. Testar cenários: dentro da tolerância (não notifica), fora da tolerância sem tomada (notifica), fora da tolerância com tomada confirmada (não notifica), dia da semana errado (não verifica).

## Observações

A tolerância de 10 minutos e a releitura completa dos arquivos a cada verificação são decisões adequadas ao estágio atual do projeto — devem ser revisitadas se o volume de dados crescer ou se a regra de tolerância precisar variar por medicamento.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro