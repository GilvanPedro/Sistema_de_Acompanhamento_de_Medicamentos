# ADR-0063 — Recuperação de senha por e-mail

## Status

Aceito (código testado; o envio real de e-mail depende de configurar a Brevo e a migração V12)

## Contexto

Quem esquecia a senha ficava sem acesso à conta (e aos remédios). Não havia como recuperá-la.

## Decisão

**"Esqueci minha senha"** no app (e na interface Swing): a pessoa digita o e-mail e recebe um **link** para escolher uma senha nova.

- **Pedido:** `POST /api/v1/auth/esqueci-senha` `{email}`. A resposta é **sempre a mesma** (202, "se este e-mail tiver uma conta…"), exista a conta ou não, para ninguém descobrir quais e-mails estão cadastrados.
- **Link de uso único e curto:** token aleatório de 256 bits; no banco fica **só o hash** (SHA-256), então quem lesse o banco não redefiniria a senha de ninguém (migração **V12**, tabela `redefinicao_senha`). Vale **30 minutos**, só uma vez, e um pedido novo invalida o anterior.
- **Página de nova senha no navegador** (`GET/POST /redefinir-senha`), servida pela própria API: funciona em qualquer aparelho, sem depender de "links profundos" do app. A página não fica em cache, não envia o endereço adiante (`Referrer-Policy: no-referrer`) e não abre dentro de outro site.
- **A senha é conferida antes de gastar o link**: se for fraca (mínimo de 8 caracteres) ou as duas não forem iguais, a pessoa corrige na mesma página.
- **Depois da troca:** todas as sessões abertas da conta são encerradas e a pessoa recebe um e-mail avisando que a senha foi alterada.
- **Limites** (em memória, como os demais): 5 pedidos por hora por IP e 3 por hora por e-mail (não dá para encher a caixa de alguém); 20 envios do formulário por hora por IP.
- **O envio roda em segundo plano** (um trabalhador só): a resposta não demora mais quando a conta existe, e uma falha do provedor de e-mail é só registrada no log, sem mudar a resposta.

### Provedor de e-mail: Brevo, por HTTPS

O plano gratuito do Render **bloqueia as portas de SMTP**, então não dá para usar Gmail/SMTP direto. O envio usa a **API HTTP da Brevo** (plano grátis: 300 e-mails por dia), configurada por variáveis de ambiente:

| Variável | Para quê |
|---|---|
| `EMAIL_BREVO_CHAVE` | chave de API da Brevo (segredo, só no Render) |
| `EMAIL_REMETENTE` | e-mail que aparece como remetente, já validado na Brevo |
| `EMAIL_NOME_REMETENTE` | opcional; nome do remetente (padrão: CuidaMed) |
| `URL_PUBLICA` | opcional; endereço da API usado no link (padrão: o do Render) |

Sem `EMAIL_BREVO_CHAVE` e `EMAIL_REMETENTE`, o envio fica **desligado**: o pedido responde normalmente, mas nada é enviado (e o log avisa, sem escrever o link, que é um segredo). O provedor está atrás de uma interface (`EnviadorDeEmail`); trocar por outro é escrever uma classe.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| SMTP (Gmail etc.) | O Render gratuito bloqueia as portas 25, 465 e 587 |
| Código de 6 dígitos digitado no app | Precisa de tela e de limite de tentativas contra adivinhação; o link com token de 256 bits não pode ser adivinhado |
| Abrir o app pelo link (deep link) | Exige verificar o domínio do servidor com o app; a página web funciona em qualquer lugar |
| Mostrar a mensagem "e-mail não cadastrado" | Revelaria quem tem conta |

## Consequências

- O e-mail passa a ser enviado por uma operadora nova (Brevo), que recebe o endereço e o nome da pessoa **só** no envio do link. **A política de privacidade lista as operadoras e ainda não cita a Brevo**: ao ligar o recurso, vale incluí-la e aumentar a versão da política (todos precisarão aceitar de novo).
- Quem perde acesso ao próprio e-mail continua sem conseguir recuperar a conta por conta própria.
- A Brevo pode pedir a validação do e-mail remetente (e, para não cair em spam, é melhor usar um domínio próprio com SPF/DKIM).

## Como ligar

1. Rodar `V12__criar_redefinicao_senha.sql` na Neon.
2. Criar a conta na Brevo, validar o e-mail remetente e gerar a chave de API.
3. No Render, definir `EMAIL_BREVO_CHAVE` e `EMAIL_REMETENTE` e publicar.
