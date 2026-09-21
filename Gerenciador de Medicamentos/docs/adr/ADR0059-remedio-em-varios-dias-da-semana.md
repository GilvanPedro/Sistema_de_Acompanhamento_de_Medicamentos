# ADR-0059 — Remédio em vários dias da semana

## Status

Aceito (servidor e app testados; vale sem mudar o banco nem a API)

## Contexto

Um remédio tinha um único dia da semana. Quem toma o mesmo remédio de segunda, quarta e sexta (ou todo dia) precisava cadastrar o mesmo remédio várias vezes.

## Decisão

Na tela de cadastro (app e interface Swing) dá para **marcar vários dias**, com um botão "Todos os dias". Por baixo, **cada dia continua sendo um registro** (mesmo nome, tipo e horário; dia diferente). No app, a lista, o "já tomei" e a exclusão tratam os registros de mesmo nome, tipo e horário como **um remédio só** ("Segunda, quarta e sexta").

Ao editar um remédio no app:
- os dias mantidos são atualizados (nome, tipo, horário);
- um dia que saiu dá lugar a um dia que entrou **reaproveitando o registro**, então o histórico dele não se perde;
- o que sobra é excluído e o que falta é criado.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Guardar uma lista de dias por remédio (coluna nova) | Mudaria o banco, a API, os alarmes, o histórico e a cópia local do app, e quebraria as versões antigas do app |
| Marcar todos os dias com um valor "todos" | Não serve para "segunda, quarta e sexta" |

## Consequências

- Nenhuma migração, nenhuma mudança na API: versões antigas do app continuam funcionando (mostram um cartão por dia).
- Os alarmes, os avisos de esquecimento e o histórico continuam por registro, sem mudança.
- Dois remédios diferentes com o mesmo nome, tipo e horário aparecem juntos na lista do app.
- Na interface Swing a edição continua de um dia por vez.
