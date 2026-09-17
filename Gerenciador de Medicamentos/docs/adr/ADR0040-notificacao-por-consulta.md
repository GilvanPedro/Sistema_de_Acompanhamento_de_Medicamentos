# ADR-0040 — Notificações por consulta (pull), específicas para quem está logado

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** O modelo original de notificação (`NotificarPort`/`ConsoleNotificationAdapter`, ADR-0004) "empurrava" a mesma mensagem para todos os familiares de um idoso de uma vez, impressa toda junto no console compartilhado — sem noção de quem estava, de fato, usando o sistema naquele momento.
- **Por que esse problema é importante?** Com o terminal interativo (login por pessoa), esse modelo deixou de fazer sentido: se três familiares cuidam do mesmo idoso, o modelo antigo faria os três "receberem" a mesma notificação de uma vez, mesmo que só um deles esteja de fato usando o sistema naquele momento. O requisito é que cada pessoa veja, ao entrar, apenas o que é relevante para ela.
- **Limitações e requisitos envolvidos:** o cálculo de "isso está atrasado?", "isso já foi tomado?" já existia (`VerificarAtrasoMedicamentoService`, ADR-0014) — o que faltava era uma forma de obter esse resultado por idoso, sob demanda, em vez de como efeito colateral de um evento.
- **Situação atual a modificar:** `RegistrarTomadaService` chamava `NotificarPort` diretamente ao salvar uma tomada, empurrando a notificação para todos os familiares vinculados de uma vez, independentemente de quem estivesse com o sistema aberto.

## Decisão

Criar `VerificarNotificacoesIdosoCase`/`VerificarNotificacoesIdosoService`, que calcula as notificações relevantes para um idoso específico (lembrete, tomado, esquecido) e devolve uma lista estruturada (`NotificacaoMedicamento`), consultada sob demanda no momento em que uma tela de console é aberta — não mais disparada como efeito colateral de uma ação de outro usuário.

- **O que será utilizado ou alterado?** Novos tipos `TipoNotificacao` (enum) e `NotificacaoMedicamento` (modelo de domínio); nova interface `VerificarNotificacoesIdosoCase` e serviço `VerificarNotificacoesIdosoService`; `RegistrarTomadaService` deixa de chamar `NotificarPort` (ver ADR-0040).
- **Como a solução será aplicada?** `TelaIdoso` chama a consulta para o próprio idoso logado; `TelaFamiliar` chama a mesma consulta para cada idoso que o familiar logado acompanha — cada sessão de console só processa e exibe o que pertence a quem está, de fato, naquela sessão.
- **Por que essa alternativa foi escolhida?** Reflete corretamente o modelo real de uso: cada pessoa, ao abrir o sistema, quer saber o que é relevante para ela, não receber uma transmissão genérica que ignora quem está de fato olhando a tela.

## Alternativas consideradas

### Alternativa 1 — Consulta sob demanda (pull), calculada por idoso, exibida na sessão de quem está logado

**Vantagens:**
- Cada pessoa vê apenas o que é relevante para ela, no momento em que ela está de fato usando o sistema.
- Reaproveita a lógica de cálculo já existente (`VerificarAtrasoMedicamentoService`), sem duplicar a regra de tolerância e checagem de "já tomado".
- Funciona igualmente bem numa futura API (a consulta vira um endpoint chamado sob demanda pelo cliente), sem carregar consigo a premissa de "console compartilhado" do modelo antigo.

**Desvantagens:**
- Notificações não chegam de forma proativa — se a pessoa nunca abrir o sistema, nunca vê o aviso (mas isso é inerente a um sistema sem notificação push real, como o CuidaMed ainda é).

### Alternativa 2 — Manter o modelo de push, mas filtrar por quem está logado no momento do evento

Continuar dependendo de `NotificarPort` no momento em que a tomada é registrada, mas checar se algum familiar está "logado" e enviar só para ele.

**Vantagens:**
- Mantém o disparo automático no momento exato em que o evento acontece.

**Desvantagens:**
- Acopla o registro de uma tomada ao estado de sessão de outras pessoas — um conceito de infraestrutura de console (`SessaoAtual`, ADR-0038) vazando para dentro da camada de aplicação, que deveria ser agnóstica de como a pessoa está interagindo com o sistema.
- Não escala para múltiplos familiares logados ao mesmo tempo sem introduzir uma lista de "quem está online agora", complexidade desproporcional ao problema.

### Alternativa 3 — Fila de notificações persistente, com cada pessoa "consumindo" as suas ao logar

Guardar as notificações geradas em algum lugar persistente (um novo arquivo CSV, por exemplo), e cada pessoa consome/marca como lida as suas ao entrar.

**Vantagens:**
- Preserva notificações mesmo que a pessoa não tenha estado logada no momento exato do evento, permitindo histórico de avisos.

**Desvantagens:**
- Introduz um novo arquivo de persistência e um novo conceito (fila, "lida"/"não lida") sem necessidade comprovada neste estágio — a informação necessária (o que está atrasado, o que foi tomado) já pode ser recalculada a qualquer momento a partir do histórico e dos medicamentos existentes, sem precisar ser "armazenada" à parte.

## Justificativa

- **Compatibilidade com o projeto:** reaproveita diretamente a lógica de tolerância e checagem de tomada já validada na ADR-0014, sem duplicar regra de negócio.
- **Facilidade de desenvolvimento:** calcular sob demanda é mais simples do que gerenciar estado de "quem já viu o quê" — não há necessidade de marcação de lida/não lida neste estágio.
- **Compatibilidade futura:** o modelo de consulta sob demanda se traduz diretamente para uma futura API (um endpoint chamado pelo cliente), enquanto o modelo de push dependia de um console compartilhado que deixa de existir.

## Consequências

**Positivas**
- Cada pessoa vê apenas as notificações relevantes para ela, resolvendo o problema de "receber o mesmo aviso três vezes" que o modelo antigo causaria com múltiplos familiares.
- A lógica de cálculo de atraso/tomada existe uma única vez, reaproveitada tanto pela verificação em lote (`VerificarAtrasoMedicamentoService`, ainda usada pelo agendador) quanto pela consulta por idoso.

**Negativas**
- Sem uma pessoa abrir o sistema, nenhuma notificação é "vista" — o modelo depende inteiramente de consulta ativa, sem nenhum mecanismo de alerta proativo (isso é uma limitação conhecida da persistência atual em CSV, sem canal de notificação real como Firebase, ADR-0004).
- `NotificarPort`/`ConsoleNotificationAdapter`/`MensagemNotificacao` continuam existindo no código, mas sem nenhum consumidor real após a ADR-0040 — ficam como infraestrutura pronta para quando um canal de notificação proativo (Firebase) for implementado.

## Impactos

Afeta `domain/model/TipoNotificacao` (novo), `domain/model/NotificacaoMedicamento` (novo), `domain/port/in/VerificarNotificacoesIdosoCase` (novo), `application/service/VerificarNotificacoesIdosoService` (novo), `config/AppConfig` (novo método de montagem), e as telas de console `TelaIdoso`/`TelaFamiliar`, que passam a consumir essa consulta ao abrir.

## Implementação

1. Criar o enum `TipoNotificacao` (LEMBRETE, TOMADO, ESQUECIDO) e o modelo `NotificacaoMedicamento`.
2. Criar `VerificarNotificacoesIdosoCase` e `VerificarNotificacoesIdosoService`, reaproveitando a lógica de tolerância/checagem de tomada já usada em `VerificarAtrasoMedicamentoService`.
3. Chamar essa consulta em `TelaIdoso.exibir()` (para o próprio idoso logado) e em `TelaFamiliar.exibir()` (para cada idoso vinculado ao familiar logado).
4. Testar: idoso vendo lembrete/aviso de atraso apenas dos próprios medicamentos; familiar vendo o status dos idosos que acompanha; nenhuma duplicação de mensagem quando mais de um familiar cuida do mesmo idoso.

## Observações

Esta decisão trabalha em conjunto com a ADR-0040 (remoção do disparo de notificação de dentro do `RegistrarTomadaService`) — as duas juntas completam a migração do modelo de push para o de pull.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro