# ADR-0062 — Alarme de remédio no lugar da notificação

## Status

Aceito (código testado; a conferência do alarme no celular depende de instalar o app novo)

## Contexto

Idosos costumam não olhar as notificações. O lembrete do horário do remédio era uma notificação que toca uma vez e some da atenção.

## Decisão

Na hora do remédio (e no aviso de atraso, 10 minutos depois, se não marcou), o app **dispara um alarme**, como o despertador do celular:

- **Marcação exata como despertador** (`setAlarmClock`): dispara mesmo em modo de economia e permite ligar a tela.
- **Serviço em primeiro plano** (`ServicoDoAlarme`, tipo `mediaPlayback`) toca o som de alarme do aparelho **em repetição** (canal de áudio "alarme", que não fica mudo com o celular no silencioso) e **vibra**, até a pessoa tocar em **"Já tomei"** ou **"Parar o alarme"**, ou passar **1 minuto** (então o som para, a notificação fica e, se ainda não tomou, o aviso de atraso vem aos 10 minutos).
- **Tela do alarme** (`AlarmeActivity`) por cima da tela de bloqueio, ligando a tela: letras grandes, dois botões. Abre por notificação de tela cheia; se o app já está à frente, abre direto. Com o celular **bloqueado**, ela mostra só "Seu remédio das 08:00", sem o nome (mesmo cuidado das notificações: dado de saúde não fica à vista).
- O "já tomei" feito em qualquer lugar (tela do alarme, botão da notificação, dentro do app) cala o alarme daquele remédio.
- No Android 14 ou mais novo, a permissão de **notificações em tela cheia** pode estar desligada; o app mostra um cartão com o botão para permitir. Sem ela, o alarme toca e a notificação aparece, mas a tela grande não abre sozinha.
- A agenda semanal, o reagendamento depois de reiniciar o celular e o "já tomei" sem internet continuam como antes (ADRs anteriores).

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Notificação com som mais alto | Toca uma vez; é o problema de partida |
| Abrir a tela direto do alarme | O Android bloqueia abrir telas a partir do segundo plano; a notificação de tela cheia é o caminho permitido |
| Aumentar o volume do alarme por conta própria | Mexer em configuração do celular da pessoa sem ela saber |

## Consequências

- O ícone de alarme aparece na barra de status enquanto houver um remédio marcado.
- Se o volume do alarme do celular estiver no zero, só a vibração e a tela avisam.
- Ao publicar na Play Store, a permissão de tela cheia e o uso de alarmes exatos entram na revisão de políticas.
