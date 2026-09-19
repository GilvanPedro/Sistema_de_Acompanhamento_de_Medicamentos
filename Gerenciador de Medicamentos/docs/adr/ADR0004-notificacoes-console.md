# ADR-0004 — Simulação de notificações via console como primeiro adapter

## Status

Substituído pelo [ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md) (o adaptador de notificação por console foi removido; o aviso agora é push, ADR-0050 e ADR-0051)

## Contexto

- **Qual problema precisa ser resolvido?** O sistema precisa notificar idosos e familiares sobre medicamentos, mas a integração real com o Firebase ainda não está pronta.
- **Por que esse problema é importante?** A regra de "quando notificar e quem notificar" (ADR-0003) precisa existir e ser validada antes de depender de qualquer serviço externo.
- **Limitações e requisitos envolvidos:** o projeto ainda não tem credenciais nem configuração de Firebase prontas; depender disso agora bloquearia o desenvolvimento do restante da lógica.
- **Situação atual a modificar:** antes desta decisão, não havia nenhuma forma de disparar ou visualizar os avisos gerados pelo sistema.

## Decisão

Definir a porta `NotificarPort` no domínio, com uma implementação inicial em `ConsoleNotificacaoAdapter`, que imprime as mensagens no terminal no lugar de enviar notificações de verdade.

- **O que será utilizado ou alterado?** Criação da interface `NotificarPort` em `domain/port/out`, e da classe `ConsoleNotificacaoAdapter` em `adapter/out/notification`.
- **Como a solução será aplicada?** Toda vez que a regra de negócio precisar notificar alguém, ela chama a `NotificarPort`, que hoje aponta para a implementação de console.
- **Por que essa alternativa foi escolhida?** Permite validar a lógica de notificação sem nenhuma dependência externa, mantendo o projeto rodando de ponta a ponta desde já.

## Alternativas consideradas

### Alternativa 1 — Adapter de console (implementação temporária)

Notificações impressas no terminal, atrás da porta `NotificarPort`.

**Vantagens:**
- Não depende de credenciais ou configuração externa.
- Permite testar e validar a lógica de disparo imediatamente.

**Desvantagens:**
- Não gera nenhuma notificação real para o usuário final enquanto for a única implementação.

### Alternativa 2 — Integração direta com Firebase desde já

Implementar o `FirebaseNotificacaoAdapter` como primeira e única forma de notificação.

**Vantagens:**
- Notificações reais desde o início do projeto.

**Desvantagens:**
- Exige configurar credenciais e projeto no Firebase antes de conseguir testar qualquer fluxo.
- Acopla o desenvolvimento da regra de negócio à disponibilidade de um serviço externo.

### Alternativa 3 — Nenhuma notificação por enquanto (adapter vazio/no-op)

Implementar a porta com métodos vazios, sem nenhum efeito visível.

**Vantagens:**
- Zero esforço de implementação.

**Desvantagens:**
- Impossível verificar se a lógica de "quem deve ser notificado e quando" está correta.
- Esconde erros que só apareceriam ao ver a saída das notificações.

## Justificativa

- **Facilidade de desenvolvimento:** o console é a forma mais rápida de validar a lógica de notificação sem infraestrutura extra.
- **Testabilidade:** permite verificar visualmente (e futuramente via teste automatizado) se os avisos certos estão sendo disparados para as pessoas certas.
- **Acoplamento:** por estar atrás de `NotificarPort`, a troca futura pelo Firebase não afeta o domínio nem os serviços de aplicação.
- **Custo:** nenhum custo de configuração externa nesta fase do projeto.

## Consequências

**Positivas**
- Lógica de notificação validada de ponta a ponta sem dependência externa.
- Base pronta para o `FirebaseNotificacaoAdapter` só precisar implementar o mesmo contrato.

**Negativas**
- Nenhuma notificação real chega ao usuário final enquanto o Firebase não for integrado.
- Necessário lembrar de trocar a configuração (`config/AppConfig`) quando o adapter real estiver pronto.

## Impactos

Afeta `domain/port/out/NotificarPort` e `adapter/out/notification/ConsoleNotificacaoAdapter`, além de qualquer serviço de aplicação que dispare notificações (ex: `RegistrarTomadaService`).

## Implementação

1. Criar a interface `NotificarPort` em `domain/port/out`.
2. Migrar a lógica hoje em `NotificacoesApi` para `ConsoleNotificacaoAdapter`, implementando `NotificarPort`.
3. Apontar o `AppConfig` para usar `ConsoleNotificacaoAdapter` como implementação atual.
4. Validar as três notificações (lembrete, remédio tomado, remédio esquecido) no cenário de teste do `Main`.

## Observações

A troca para o `FirebaseNotificacaoAdapter` é um passo futuro já previsto na estrutura, mas ainda sem ADR própria — será registrada quando a integração for de fato implementada.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro