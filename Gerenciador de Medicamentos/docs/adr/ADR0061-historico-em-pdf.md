# ADR-0061 — Histórico em PDF para levar ao médico

## Status

Aceito

## Contexto

O familiar ou o idoso precisa levar o histórico de remédios a uma consulta, de um período à escolha (por exemplo, de 3 de agosto a 4 de setembro).

## Decisão

- **O servidor gera o PDF**: `GET /api/v1/idosos/{id}/historico.pdf?de=aaaa-mm-dd&ate=aaaa-mm-dd`, com a mesma regra de acesso do histórico (o idoso, ou o familiar vinculado). Período de no máximo um ano.
- O documento traz o nome, o período, o resumo (quantos tomou, quantos não tomou, a porcentagem) e uma linha por horário, com a situação **escrita** (não só em cor), incluindo os "não tomou" (ADR-0060). Uma nota explica o que cada situação significa e que o documento não substitui a avaliação do médico.
- Gerado com a biblioteca **OpenPDF** (licença LGPL/MPL): um jar, sem serviço externo.
- No app, "Baixar histórico em PDF" abre um calendário de período (com atalhos "Últimos 30 dias" e "Últimos 3 meses"); depois de baixar, o app oferece **Abrir** e **Compartilhar ou imprimir** (WhatsApp, e-mail, Drive, impressora). O arquivo fica no cache do app (só o mais recente é mantido) e é entregue a outros apps por um `FileProvider` restrito a essa pasta.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Gerar o PDF no celular | Os "não tomou" são calculados no servidor; teria de duplicar a regra e ela só funcionaria com o histórico completo baixado |
| Imprimir a tela do app | Sem controle de período nem de layout |

## Consequências

- Baixar o PDF exige internet (o servidor no plano gratuito pode levar cerca de um minuto para acordar).
- Os dados de saúde saem do app para onde a pessoa escolher compartilhar: a decisão é dela.
