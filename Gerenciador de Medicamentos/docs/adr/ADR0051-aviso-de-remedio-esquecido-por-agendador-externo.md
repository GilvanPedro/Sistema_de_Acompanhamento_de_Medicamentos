# ADR-0051 — Aviso de "remédio esquecido" na hora, com agendador externo

## Status

Aceito

## Contexto

O push instantâneo (ADR-0050) só dispara quando alguém **faz** alguma coisa: o idoso marca "já tomei", o familiar pede um vínculo, e assim por diante. O aviso mais importante para o familiar é justamente o contrário: o idoso **não fez nada**. Não existe ação que dispare o push, então algo precisa olhar o relógio.

Duas restrições pesam:

- O servidor fica no plano gratuito do Render, que **hiberna** depois de cerca de 15 minutos sem requisição. Uma rotina interna (`@Scheduled`) simplesmente não roda com o servidor dormindo.
- A verificação do app em segundo plano (WorkManager) só roda a cada 15 minutos, no mínimo, e só se o aparelho do familiar deixar.

## Decisão

### 1. Uma rota que o agendador chama

`POST /api/v1/interno/atrasos` procura remédios esquecidos e manda o push aos familiares. Ela não usa o login de uma pessoa (quem chama é um serviço): exige o segredo no cabeçalho `X-Cron-Segredo`.

- O segredo vem da variável de ambiente **`CRON_SEGREDO`** (mínimo de 16 caracteres). **Sem ela, a rota responde 404**, como se não existisse.
- A comparação é em tempo constante (`MessageDigest.isEqual`).
- Segredo errado é limitado por IP: 5 erros em 15 minutos, depois 429 (o mesmo mecanismo do login).
- A rota fica fora do filtro de token de pessoa (`/api/v1/interno/`), porque tem a sua própria proteção.

### 2. Como decide quem avisar (`VerificadorDeAtrasos`)

Para cada idoso **com pelo menos um familiar vinculado**, usa o `VerificarNotificacoesIdosoService` (a mesma regra de atraso que o app e a GUI já usam: passou de 10 minutos do horário e não há tomada que cubra). Para cada remédio esquecido:

- **Uma vez por remédio por dia.** A tabela `aviso_atraso_enviado (medicamento_id, dia)` (migração V6) recebe `INSERT ... ON CONFLICT DO NOTHING`. Só quem conseguiu inserir envia o aviso. Isso é atômico no banco, então duas chamadas simultâneas (ou um reenvio do agendador) nunca duplicam. Linhas com mais de 7 dias são apagadas na própria inserção.
- **Um push por familiar por verificação**, mesmo que vários remédios estejam atrasados.
- Idosos sem familiar ficam de fora: eles já têm o alarme e o aviso de atraso no próprio celular.

### 3. O push é o mesmo de sempre

Vai só o "há novidade", sem dado pessoal. O app acorda, busca `/idosos/{id}/notificacoes` e mostra "não tomou" com o controle de repetição que já existia. **O app não precisou mudar.**

### 4. O agendador

Um job no **cron-job.org** (gratuito) faz `POST` na rota **a cada 5 minutos**, com o cabeçalho `X-Cron-Segredo`. Um efeito colateral bom: o servidor nunca hiberna, o que também acelera o primeiro login do dia.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| `@Scheduled` dentro do servidor | O servidor hiberna: não roda quando mais precisa |
| Só a verificação do app (WorkManager) | No mínimo 15 minutos, e o Android pode adiar |
| Cron job do Render | É recurso pago |
| GitHub Actions com `schedule` | Atrasa e falha em horários de pico, e o segredo teria de ir para o repositório |

## Consequências

- O familiar recebe o aviso em **até ~5 minutos depois dos 10 minutos de tolerância** (medido no teste real: o push chegou ao celular 3 segundos depois de o servidor enviar).
- Passa a existir uma **dependência externa**. Se o cron-job.org sair do ar, o aviso volta a depender da verificação de 15 minutos do app, que continua como rede de segurança.
- Uso do plano gratuito do Render: manter o servidor sempre acordado consome horas do mês (um serviço 24 h fica em cerca de 744 h, dentro das 750 h gratuitas). Se houver um segundo serviço, isso estoura.
- **Diagnóstico sem ver os logs** (que o plano gratuito limita): `GET /api/v1/me/dispositivos/estado`, com login, mostra se o push está ligado e como foi o último envio.

## Como configurar

1. Rodar `V6__criar_aviso_atraso_enviado.sql` na Neon.
2. No Render, criar a variável `CRON_SEGREDO` (`openssl rand -hex 24`).
3. No cron-job.org, criar um job `POST https://<servidor>/api/v1/interno/atrasos`, a cada 5 minutos, com o cabeçalho `X-Cron-Segredo` igual ao segredo.
