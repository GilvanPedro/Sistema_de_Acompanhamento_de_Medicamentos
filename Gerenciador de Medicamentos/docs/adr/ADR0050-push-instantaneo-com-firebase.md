# ADR-0050 — Push instantâneo com o Firebase (FCM)

## Status

Aceito e em uso (testado no celular real: o aviso chega em segundos, com o app fechado)

## Contexto

Os avisos ao familiar ("não tomou", "tomou") e o pedido de vínculo ao idoso dependiam da verificação em segundo plano do Android, que só roda a cada 15 minutos (no mínimo). Mudanças feitas pelo familiar nos remédios também só chegavam ao idoso nesse ritmo. O planejamento inicial já previa o Firebase para avisos na hora.

## Decisão

### 1. O push é só um "acorde e sincronize", sem dado pessoal

A mensagem enviada pelo FCM leva apenas `{"tipo": "novidade"}` (prioridade alta). **Nome, remédio e horário nunca passam pelo Google.** Ao receber, o app roda a mesma verificação que já fazia a cada 15 minutos (`Eventos.verificarAgora`): busca no servidor, atualiza os alarmes do idoso e monta a notificação no próprio aparelho, com o mesmo controle de avisos repetidos (`EventosJaAvisados`). A rotina de 15 minutos continua como rede de segurança, porque push pode falhar ou atrasar.

Isso reduz o impacto na LGPD (o provedor de push não recebe dado de saúde) e mantém uma única lógica de aviso.

### 2. Quem é avisado, e quando (servidor)

| Acontece | Quem recebe o push |
|---|---|
| Idoso marca "já tomei" | familiares vinculados |
| Familiar pede vínculo | o idoso |
| Idoso aceita o pedido | o familiar |
| Idoso adiciona um familiar | o familiar |
| Familiar cadastra, edita ou exclui remédio | o idoso (para atualizar os alarmes) |

O envio é feito numa fila em segundo plano: uma falha ou lentidão do Google nunca atrasa nem derruba a resposta da API.

### 3. Aparelhos (tokens)

- Tabela `dispositivo` (migração **V5**): `token` (chave), `usuario_id`, `atualizado_em`. Um token pertence a uma conta por vez: se outra pessoa entrar no mesmo aparelho, o token passa para ela.
- `PUT /me/dispositivos` `{token}` registra; `DELETE /me/dispositivos` `{token}` remove (só se for da conta que pede). Sair da conta remove o token; excluir a conta remove todos.
- Token que o FCM diz não existir mais (app desinstalado) é descartado no primeiro envio que falhar por isso.
- No app, o registro acontece ao entrar e a cada verificação, mas só chama o servidor quando o token ou a conta mudam (`RegistroDePush`).

### 4. Envio pelo servidor sem SDK

`FcmNotificadorPush` usa a API HTTP v1: assina um JWT (RS256, com a biblioteca jjwt já usada) com a chave da conta de serviço, troca por um token de acesso do Google (guardado em memória até perto de expirar) e faz `POST /v1/projects/{id}/messages:send` com `java.net.http.HttpClient`. Sem SDK do Firebase Admin, para não aumentar o servidor nem as dependências.

A chave da conta de serviço vem da variável **`FIREBASE_CREDENTIALS`** (o JSON inteiro). Se não existir, entra `NotificadorPushDesligado` e a API funciona igual, sem push. **A chave nunca vai para o Git.**

### 5. App

- Dependência `firebase-messaging` (via BOM) e o plugin `google-services` aplicado **só se** `app/google-services.json` existir (arquivo fora do Git). Sem ele o app compila e roda, com o push desligado; o build de quem não tem o Firebase não quebra.
- `ServicoDePush` (`FirebaseMessagingService`) recebe a mensagem e chama `Eventos.verificarAgora`; `onNewToken` limpa a marca de registro para o novo token subir.

## Consequências

- Avisos passam de "até ~15 min" para segundos, com o app fechado (o push de prioridade alta acorda o app mesmo em economia de bateria, dentro dos limites do Android).
- O aviso de "esquecido" (o idoso *não* faz nada) não tem ação que o dispare; ele é enviado por um agendador externo, como descrito no [ADR-0051](ADR0051-aviso-de-remedio-esquecido-por-agendador-externo.md).
- Aparelhos sem serviços do Google (raro) continuam só com a verificação de 15 minutos.
- Novo custo operacional: manter a chave da conta de serviço como segredo no Render.

## Como ligar (passo a passo)

1. Console do Firebase (https://console.firebase.google.com): criar projeto e adicionar um app **Android** com o pacote `br.com.cuidamed`.
2. Baixar `google-services.json` e colocar em `app-android/app/` (o Git o ignora).
3. Configurações do projeto > **Contas de serviço** > **Gerar nova chave privada**. Colar o conteúdo do JSON na variável `FIREBASE_CREDENTIALS` no Render (e no `.env` local, se quiser testar daqui). Não commitar o arquivo.
4. Rodar `V5__criar_dispositivo.sql` na Neon (junto com a V4, se ainda não foi), publicar o servidor e recompilar o app.

## O que aprendemos ao ligar (para quem for configurar de novo)

- **O nome da variável importa.** A chave da conta de serviço é lida de `FIREBASE_CREDENTIALS` (o código também aceita o nome antigo `FIREBASE_CREDENCIAIS`). Um nome diferente do que o código lê deixa o push **desligado sem nenhum erro**.
- **Diagnóstico sem os logs do Render** (o plano gratuito não mostra os logs de requisição): `GET /api/v1/me/dispositivos/estado`, com login, diz se o push está `LIGADO` ou `DESLIGADO` e como foi o último envio (por exemplo, `FCM respondeu 200`). O servidor também escreve `Push: LIGADO` ou `Push: DESLIGADO` na partida.
- **O campo `aud` do pedido de token ao Google tem de ser um texto**, não uma lista. A biblioteca de JWT gera lista por padrão (`audience().add`); com isso o Google recusava com `invalid_grant`. O código usa `audience().single(...)`.
- O envio roda numa fila em segundo plano: uma falha do Google aparece no log (`Push: falha ao enviar ...`), nunca na resposta da API.

## Ajuste depois do uso real: avisos com o app fechado (versão 1.0.1)

Um teste real mostrou aviso faltando quando o app estava fechado. Foram corrigidos dois pontos, no app:

- **O push é tratado na hora, dentro do serviço do Firebase.** Antes, ao chegar o push, o app só *agendava* a verificação num trabalho do WorkManager (com exigência de internet), e o Android pode adiar esse trabalho por minutos com o aparelho parado. Agora `ServicoDePush.onMessageReceived` roda a verificação diretamente (o serviço tem cerca de 10 segundos, mesmo com o app morto), com limite de 8 s; só se faltar rede ou tempo é que se agenda o trabalho de antes. A lógica ficou em `VerificadorDeEventos`, usada também pelo trabalho periódico de 15 minutos.
- **O código de push deste aparelho é mantido em dia no servidor.** Sem registro (ou com um código velho), nenhum aviso chega. Agora o registro é refeito assim que o Firebase troca o código (`onNewToken`, também dentro do serviço), toda vez que o app é aberto e, no mais, pelo menos a cada hora, e não só quando o código muda. Os dados mostraram um aparelho cujo código no servidor estava diferente do do celular.

Verificado no celular real, com o app morto (`am kill`), o modo Doze forçado e os baldes de espera "restrito" e "raro": o aviso chegou em 6 a 7 segundos, e o app trocou sozinho um código inválido do servidor pelo real ao ser aberto.

**O que o app não consegue contornar:** "Forçar parada" nas configurações do Android bloqueia push e alarmes até o app ser aberto de novo, e no Samsung o app não pode estar em "Apps em suspensão" ou "em suspensão profunda" (Configurações > Bateria > Limites de uso em segundo plano).

