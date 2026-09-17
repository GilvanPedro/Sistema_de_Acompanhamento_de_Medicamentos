# ADR-0040 — Remoção do disparo direto de notificação em `RegistrarTomadaService`

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `RegistrarTomadaService` (ADR-0013) chamava `NotificarPort.avisarRemedioTomado`/`avisarRemedioEsquecido` diretamente ao salvar uma tomada, empurrando a mensagem para todos os familiares vinculados de uma vez — o modelo que a ADR-0039 substituiu por consulta sob demanda.
- **Por que esse problema é importante?** Com a ADR-0039 em vigor, manter essa chamada dentro de `RegistrarTomadaService` deixaria um caminho de código morto (o `NotificarPort` sendo acionado, mas sem que o modelo de exibição do terminal dependa dele) e, pior, um resquício do modelo antigo que poderia ser reativado por engano se alguém reconectasse essa chamada a uma tela futura sem perceber que contraria a decisão de pull tomada na ADR-0039.
- **Limitações e requisitos envolvidos:** `RegistrarTomadaService` deve continuar responsável por salvar o histórico — essa parte não muda; só a notificação síncrona sai.
- **Situação atual a modificar:** o corpo de `registrarTomada`, que chamava `notificarPort.avisarRemedioTomado(idoso, medicamento)` ou `avisarRemedioEsquecido(idoso, medicamento)` logo após salvar o histórico.

## Decisão

Remover a dependência de `NotificarPort` de `RegistrarTomadaService`, deixando-o responsável apenas por gerar o id e salvar o histórico.

- **O que será utilizado ou alterado?** `RegistrarTomadaService` perde o parâmetro `NotificarPort` do construtor e a chamada de notificação do corpo do método; `AppConfig.criarRegistrarTomadaService()` ajustado para não passar mais esse parâmetro.
- **Como a solução será aplicada?** O método `registrarTomada` passa a fazer só duas coisas: gerar o id e salvar o `HistoricoMedicamento`.
- **Por que essa alternativa foi escolhida?** É a consequência direta da ADR-0039 — se a exibição de notificação passou a ser por consulta (pull), mantida do lado de quem consulta (`VerificarNotificacoesIdosoService`), o disparo do lado de quem escreve (`RegistrarTomadaService`) se torna redundante e potencialmente confuso.

## Alternativas consideradas

### Alternativa 1 — Remover a notificação de `RegistrarTomadaService` por completo

**Vantagens:**
- Elimina o resquício do modelo de push, coerente com a decisão da ADR-0039.
- `RegistrarTomadaService` fica com responsabilidade única e clara: registrar a tomada.

**Desvantagens:**
- Nenhuma identificada — é a consequência natural da decisão já tomada na ADR-0039.

### Alternativa 2 — Manter a chamada de notificação, mas sem nenhuma tela do terminal a utilizando

**Vantagens:**
- Nenhuma vantagem real — só manteria código morto.

**Desvantagens:**
- Deixa uma chamada ativa (`ConsoleNotificationAdapter` continuaria imprimindo no console) que compete visualmente com o modelo novo de notificação, gerando confusão sobre qual é o comportamento "de verdade" do sistema.
- Risco de alguém reativar esse caminho inadvertidamente no futuro, sem perceber que contraria a ADR-0039.

### Alternativa 3 — Adaptar a chamada de notificação para também considerar quem está logado, em vez de removê-la

Tentar fazer `RegistrarTomadaService` checar `SessaoAtual` antes de notificar, evitando duplicidade.

**Vantagens:**
- Manteria alguma forma de notificação síncrona no momento do evento.

**Desvantagens:**
- Acoplaria `application/service` (que deveria ser agnóstico de forma de entrada) a `adapter/in/console/SessaoAtual` — quebra direta do princípio da Arquitetura Hexagonal (ADR-0001) e do próprio motivo pelo qual `SessaoAtual` foi isolada no pacote de console (ADR-0038).

## Justificativa

- **Compatibilidade com o projeto:** consequência direta e necessária da ADR-0039 — as duas decisões precisam andar juntas para o modelo de notificação fazer sentido de ponta a ponta.
- **Manutenibilidade:** evita a existência de dois caminhos de notificação simultâneos (push morto e pull ativo) que poderiam confundir quem lê o código depois.
- **Responsabilidade única:** `RegistrarTomadaService` volta a ter um propósito só — registrar o fato de uma tomada ter (ou não) acontecido.

## Consequências

**Positivas**
- Elimina a ambiguidade entre dois modelos de notificação coexistindo no código.
- `RegistrarTomadaService` fica mais simples e com escopo mais claro.

**Negativas**
- `NotificarPort`, `ConsoleNotificationAdapter` e `MensagemNotificacao` ficam sem nenhum consumidor ativo no sistema — permanecem como infraestrutura pronta para um canal de notificação proativo futuro (Firebase, ADR-0004), mas atualmente não são chamados por nenhum fluxo.

## Impactos

Afeta `application/service/RegistrarTomadaService` (construtor e corpo simplificados) e `config/AppConfig` (ajuste em `criarRegistrarTomadaService()`).

## Implementação

1. Remover o parâmetro `NotificarPort` do construtor de `RegistrarTomadaService`.
2. Remover a chamada a `notificarPort.avisarRemedioTomado`/`avisarRemedioEsquecido` do corpo de `registrarTomada`.
3. Ajustar `AppConfig.criarRegistrarTomadaService()` para não passar mais esse parâmetro.
4. Testar registrando uma tomada e confirmando que o histórico é salvo normalmente, sem nenhuma saída de notificação disparada nesse momento.

## Observações

`NotificarPort` e suas implementações continuam no projeto, sem uso ativo. Quando um canal de notificação proativo real (Firebase) for implementado, ele deve ser acionado a partir de um ponto de decisão consciente (por exemplo, o próprio agendador de verificação de atraso, ADR-0015), não reintroduzido dentro de `RegistrarTomadaService`.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro