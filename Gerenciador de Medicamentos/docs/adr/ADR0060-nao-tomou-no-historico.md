# ADR-0060 — "Não tomou" no histórico

## Status

Aceito (V11 precisa ser aplicada na Neon antes de publicar o servidor)

## Contexto

O histórico só tinha as tomadas marcadas. Um remédio esquecido simplesmente não aparecia, e quem olhava (o próprio idoso, o familiar ou o médico) não conseguia ver as falhas.

## Decisão

Um horário previsto **que terminou sem tomada vira "Não tomou"** no histórico, mostrado no **horário em que era para tomar**.

- **Calculado na leitura, não gravado.** O servidor cruza os horários previstos de cada remédio com as tomadas registradas (`FaltasDeMedicamento`). Assim a falta some sozinha se a tomada chegar depois (o celular pode ficar dias sem internet e o "já tomei" sobe atrasado) e nunca fica desatualizada. As faltas saem com id negativo (não existem no banco).
- **Só depois de fechado.** O horário vira falta quando o dia dele acabou e passou a folga de 3 horas da madrugada (a mesma regra dos avisos de atraso, ADR-0047), e não antes.
- **Só a partir de quando o horário vale.** Migração **V11**: `medicamento.vigente_desde`. Sem ela, um remédio cadastrado hoje teria "não tomou" em todas as semanas passadas. A data começa no cadastro e **reinicia quando o dia ou o horário mudam** (trocar só nome ou tipo não reinicia). Os remédios que já existem começam a valer na hora da migração: não inventamos faltas do passado.
- Olha até um ano para trás. Remédio excluído não aparece (as tomadas dele também não).
- O app ajusta a visão local: um "não tomou" some assim que existe, no aparelho, uma tomada ainda não enviada que o cubra.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Gravar a falta com uma tarefa agendada à meia-noite | Depende do agendador rodar todo dia; a falta gravada ficaria errada se a tomada subisse depois; precisaria de correção posterior |
| Usar a data de criação do remédio | Um remédio que troca de horário teria faltas do horário novo nas semanas anteriores à troca |

## Consequências

- A lista do histórico da API (`GET /idosos/{id}/historico`) passa a incluir as faltas (o app antigo já mostra "Não tomou" para elas).
- É preciso rodar `V11__medicamento_vigente_desde.sql` na Neon **antes** de publicar: o servidor novo lê a coluna.
