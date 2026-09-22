# ADR-0065 — Tolerância de esquecimento sobe para 1 hora, e o "não tomado" aparece antes do fim do dia

## Status

Aceito

## Contexto

Duas coisas incomodavam no uso real:

1. **O alarme e os avisos só davam 10 minutos de chance** antes de considerar o remédio esquecido (`OcorrenciasMedicamento.TOLERANCIA_MINUTOS`, ADR-0014). É pouco tempo para um idoso perceber o celular, se levantar e tomar o remédio.
2. **O "não tomado" só aparecia no histórico no dia seguinte** (`FaltasDeMedicamento`, ADR-0060 — o horário só virava falta depois do dia inteiro acabar, mais a folga de 3h da madrugada). Um remédio esquecido de manhã não aparecia em lugar nenhum até o dia terminar, então parecia que a marcação de "não tomado" simplesmente não funcionava.

## Decisão

**A tolerância sobe de 10 minutos para 1 hora**, numa constante só (`OcorrenciasMedicamento.TOLERANCIA_MINUTOS`, espelhada no app em `HorariosDeRemedio.TOLERANCIA_MINUTOS` e `AgendadorDeLembretes.TOLERANCIA_MINUTOS`), que passa a governar as três coisas ao mesmo tempo:

1. **Quando o alarme para de insistir.** No horário marcado, o alarme toca (se ainda não foi tomado) e, enquanto não marcar, **repete a cada 10 minutos** (o "aviso de atraso": 10, 20, 30, 40 e 50 minutos depois). Ao completar 1 hora do horário original, **para de insistir nesse dia** — nem alarme nem notificação. O remédio ainda pode ser marcado como tomado a qualquer hora do resto do dia; só não há mais lembrete ativo. Cada alarme de atraso carrega o horário original (não só "10 minutos depois do anterior"), então a hora de parar é sempre em relação ao horário do remédio, mesmo depois de reiniciar o celular no meio da sequência.
2. **Quando o "não tomado" aparece no histórico.** `FaltasDeMedicamento` não espera mais o dia acabar: um horário sem tomada que o cubra vira "não tomado" assim que passa 1 hora dele, mesmo à tarde do mesmo dia. Continua **corrigível**: se a pessoa marcar como tomado depois (a qualquer hora do dia, ou de madrugada para quem toma tarde da noite, dentro da folga de 3h já existente), a entrada de "não tomado" some e vira "tomado" na próxima consulta — nada fica gravado como falta definitiva, o cálculo é sempre feito na hora.
3. **Quando o familiar é avisado.** `VerificadorDeAtrasos` usa a mesma regra (`ESQUECIDO`), então o push ao familiar também passa a chegar depois de 1 hora, não mais 10 minutos.

Nada mudou na **folga da madrugada** (3 horas, `PRAZO_APOS_MEIA_NOITE`, ADR-0060): ela continua sendo sobre quanto tempo depois da meia-noite uma tomada ainda cobre o horário de ontem (a correção), separada da tolerância acima (a visibilidade).

### Verificado: marcar antes da hora não deixa alarme nem aviso sobrando

Conferido no código (não precisou de mudança): marcar "já tomei" a qualquer momento — pela tela "Tomei um remédio" ou pelo botão da notificação/alarme — chama `Confirmacoes.marcar()` na hora, para aquele remédio **e aquele dia**. Quando o alarme do horário marcado dispara mais tarde, o recebedor confere `Confirmacoes.tomouHoje()` antes de tocar; se já foi marcado (mesmo antes da hora), o alarme não toca, e a cadeia de alarmes de atraso é cancelada (`AgendadorDeLembretes.cancelarAtraso`) — não fica repetindo à toa. O aviso "Avisos de hoje" também já mostra "Tomado" nesse caso, nunca "Lembrete" ou "Esquecido".

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Manter o "não tomado" só no fim do dia, e só mudar a tolerância do alarme | O problema relatado era justamente essa demora; separar as duas tolerâncias complicaria sem necessidade |
| Tolerâncias diferentes para alarme, histórico e aviso ao familiar | Mais uma constante para manter sincronizada; nenhum motivo prático para serem diferentes |
| Um único alarme de atraso, 1h depois do horário | Foi a primeira versão desta ADR, mas o usuário pediu para o alarme continuar chamando a atenção durante a hora inteira, não só no fim dela |

## Consequências

- O familiar passa a ser avisado de um esquecimento até 1 hora mais tarde do que antes (era 10 minutos).
- Quem olhar o histórico durante o dia agora vê "não tomado" ao vivo, não só no dia seguinte — o PDF exportado (ADR-0061) também reflete isso na hora em que for gerado.
- Não há mais motivo para reclamar que "não tomado" não aparece: ele aparece a partir de 1 hora do horário, e desaparece assim que for marcado.
