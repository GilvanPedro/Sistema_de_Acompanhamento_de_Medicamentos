# ADR-0050 — Push instantâneo com o Firebase (FCM)

## Status

Aceito (código pronto; falta o usuário criar o projeto no Firebase e configurar as chaves)

## Contexto

Os avisos ao familiar ("não tomou", "tomou") e o pedido de vínculo ao idoso dependiam da verificação em segundo plano do Android, que só roda a cada 15 minutos (no mínimo). Mudanças feitas pelo familiar nos remédios também só chegavam ao idoso nesse ritmo. O ADR-0045 já previa o Firebase para avisos na hora.

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
- **Limite conhecido:** o aviso de "esquecido" (o idoso *não* fez nada) não tem gatilho de ação. Ele continua dependendo da verificação a cada 15 minutos. Para chegar na hora, o servidor precisaria de um agendador que dispare o push nos horários dos remédios (ideia para depois; no plano gratuito do Render, que hiberna, exigiria um serviço externo de agendamento).
- Aparelhos sem serviços do Google (raro) continuam só com a verificação de 15 minutos.
- Novo custo operacional: manter a chave da conta de serviço como segredo no Render.

## Como ligar (passo a passo)

1. Console do Firebase (https://console.firebase.google.com): criar projeto e adicionar um app **Android** com o pacote `br.com.cuidamed`.
2. Baixar `google-services.json` e colocar em `app-android/app/` (o Git o ignora).
3. Configurações do projeto > **Contas de serviço** > **Gerar nova chave privada**. Colar o conteúdo do JSON na variável `FIREBASE_CREDENTIALS` no Render (e no `.env` local, se quiser testar daqui). Não commitar o arquivo.
4. Rodar `V5__criar_dispositivo.sql` na Neon (junto com a V4, se ainda não foi), publicar o servidor e recompilar o app.
