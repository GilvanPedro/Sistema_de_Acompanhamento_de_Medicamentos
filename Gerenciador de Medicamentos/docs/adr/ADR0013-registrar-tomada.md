# ADR-0013 — Unificação de registro de tomada e notificação em um único serviço

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Registrar que um medicamento foi (ou não) tomado, e notificar as pessoas certas sobre isso, eram dois passos soltos, chamados manualmente e separadamente direto no `Main`.
- **Por que esse problema é importante?** Sempre que uma tomada é registrada, uma notificação correspondente também deveria acontecer — eram, na prática, sempre chamados juntos. Deixar isso solto significa correr o risco de esquecer de notificar ao registrar, ou vice-versa.
- **Limitações e requisitos envolvidos:** o serviço precisa decidir automaticamente qual notificação disparar (remédio tomado ou remédio esquecido) com base no mesmo dado que está sendo registrado no histórico.
- **Situação atual a modificar:** o `Main` chamava `historicoPort.salvar(...)` e, separadamente, `notificarPort.avisarRemedioTomado(...)`/`avisarRemedioEsquecido(...)`, exigindo lembrar de sempre fazer os dois passos juntos e na ordem certa.

## Decisão

Criar o `RegistrarTomadaService`, implementando `RegistrarTomadaCase`, que recebe o idoso, o medicamento e se foi tomado ou não, e internamente: gera o id, salva o histórico e dispara a notificação correspondente — tudo em uma única chamada.

- **O que será utilizado ou alterado?** Nova interface `RegistrarTomadaCase` em `domain/port/in`, e nova classe `RegistrarTomadaService` em `application/service`, dependendo de `GerarIdPort`, `SalvarHistoricoPort` e `NotificarPort`.
- **Como a solução será aplicada?** Um único método `registrarTomada(idoso, medicamento, tomou)` decide, com base no `boolean tomou`, qual das duas notificações disparar, depois de salvar o histórico.
- **Por que essa alternativa foi escolhida?** Reflete o fato de que essas duas ações sempre acontecem juntas na prática — juntá-las no mesmo serviço evita duplicar essa orquestração em cada ponto de entrada futuro (console, agendador, futura API).

## Alternativas consideradas

### Alternativa 1 — Serviço único orquestrando histórico + notificação

**Vantagens:**
- Quem usa o serviço não precisa lembrar de disparar notificação manualmente — é automático.
- Centraliza a decisão de qual notificação disparar num único lugar.

**Desvantagens:**
- Acopla, dentro de um único serviço, duas responsabilidades (persistir e notificar) que poderiam, em teoria, evoluir separadamente.

### Alternativa 2 — Manter os dois passos separados, chamados manualmente

Continuar como estava: quem chama o serviço decide separadamente salvar o histórico e disparar a notificação.

**Vantagens:**
- Cada ação continua isolada e simples de entender individualmente.

**Desvantagens:**
- Risco real de esquecer de notificar (ou de salvar) em algum novo ponto de entrada, já que são duas chamadas distintas que sempre deveriam andar juntas.

### Alternativa 3 — Notificação disparada por evento, de forma desacoplada

O `SalvarHistoricoPort.salvar(...)` dispara um evento interno, e algum observador escuta esse evento para notificar.

**Vantagens:**
- Desacopla completamente quem salva de quem notifica, permitindo adicionar novos "escutadores" no futuro sem alterar o fluxo principal.

**Desvantagens:**
- Adiciona um mecanismo de eventos (publish/subscribe) que é complexidade desnecessária para o tamanho atual do projeto.

## Justificativa

- **Manutenibilidade:** um único ponto de manutenção para o fluxo "registrar tomada", em vez de espalhado pelo código que chama o serviço.
- **Facilidade de desenvolvimento:** futuros pontos de entrada (agendador, API) só precisam chamar um método, sem reimplementar a orquestração.
- **Compatibilidade com o projeto:** o padrão de responsabilidade única continua respeitado — o serviço orquestra, mas delega a persistência e a notificação para suas respectivas portas.

## Consequências

**Positivas**
- Registrar uma tomada e notificar as pessoas certas virou uma única chamada, eliminando o risco de esquecer um dos dois passos.
- Padrão reaproveitado depois pelo `VerificarAtrasoMedicamentoService` e pelo agendador.

**Negativas**
- O serviço de registrar tomada agora depende de três portas diferentes (`GerarIdPort`, `SalvarHistoricoPort`, `NotificarPort`) em vez de uma só, aumentando ligeiramente sua complexidade interna.

## Impactos

Afeta `domain/port/in/RegistrarTomadaCase` (novo), `application/service/RegistrarTomadaService` (novo), e `config/AppConfig` (novo método de montagem).

## Implementação

1. Criar a interface `RegistrarTomadaCase`.
2. Implementar `RegistrarTomadaService`, recebendo as três portas no construtor.
3. Adicionar o método de montagem correspondente no `AppConfig`.
4. Substituir, no `Main`, as chamadas separadas de histórico e notificação por uma única chamada a `registrarTomada(...)`.

## Observações

O lembrete pré-tomada (`lembrarIdoso`) ficou fora desse serviço de propósito — ele não gera histórico e acontece antes da tomada, então continua sendo chamado separadamente. Se essa distinção causar confusão no futuro, considerar um `EnviarLembreteCase` próprio.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro