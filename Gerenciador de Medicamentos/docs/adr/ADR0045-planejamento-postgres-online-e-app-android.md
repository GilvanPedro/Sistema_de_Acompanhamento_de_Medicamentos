# ADR-0045 — Planejamento: PostgreSQL online e aplicativo Android

> **Rascunho de planejamento.** Este ADR serve só para anotar o que vamos fazer a seguir e será excluído depois. Nada aqui está decidido ainda.

## Status

Proposto

## Contexto

Hoje os dados ficam em arquivos CSV locais (`arquivos/`) e a interface é Swing (desktop) ou terminal. Queremos:

1. **Trocar a persistência de CSV para um banco PostgreSQL online**, sempre disponível.
2. **Ter o sistema como aplicação de celular e de computador**, com gerenciamento e visualização das informações nos dois.

Ponto central: se o banco fica online e vários dispositivos usam o mesmo sistema, o app **não deve falar direto com o PostgreSQL**. Expor o banco na internet com usuário/senha dentro do app é um risco de segurança sério. O caminho usual é ter uma **API (backend) online** entre os apps e o banco.

## Decisão (a definir)

Plano em duas frentes, na ordem abaixo.

### Frente 1 — PostgreSQL online

- [ ] Escolher onde hospedar o PostgreSQL (ver opções abaixo).
- [ ] Modelar as tabelas: `usuario`, `idoso`, `familiar`, `vinculo_idoso_familiar`, `medicamento`, `historico_tomada`.
- [ ] Criar scripts de criação do schema (migrations, ex.: Flyway).
- [ ] Criar adaptadores `adapter/out/persistence/postgres` implementando as **mesmas portas** de `domain/port` (a arquitetura hexagonal já permite trocar o CSV sem mexer em `domain/` e `application/`).
- [ ] Usar JDBC com `PreparedStatement` (evitar SQL injection) e pool de conexões (HikariCP).
- [ ] Trocar a geração de ids: hoje é "maior id do CSV + 1"; no PostgreSQL passa a ser `GENERATED ... AS IDENTITY` (ou `SERIAL`). Revisar `adapter/out/id` (ADR-0007 e ADR-0011).
- [ ] Guardar credenciais em variáveis de ambiente, nunca no repositório.
- [ ] Script para migrar os dados atuais dos CSV para o PostgreSQL.
- [ ] Decidir o que fazer com os CSV (manter como opção offline ou remover).

### Frente 2 — Aplicação de celular e computador

- [ ] Criar uma **API REST** (backend) que expõe os serviços de `application/service` — vira um novo adaptador de entrada (`adapter/in/web`, já previsto na arquitetura).
- [ ] **Autenticação**: login retorna um token (JWT); a senha continua com BCrypt. HTTPS obrigatório.
- [ ] **Autorização**: idoso só vê o que é dele; familiar só vê idosos vinculados (regras já existentes, agora aplicadas por requisição em vez de `SessaoAtual`).
- [ ] Escolher a tecnologia do cliente (ver opções abaixo).
- [ ] Reaproveitar o design da GUI atual (fontes grandes, tema claro/escuro, poucas opções por tela).
- [ ] **Notificações**: hoje o `AgendadorVerificacaoAtraso` não roda na GUI. Com backend online, ele pode rodar no servidor e mandar **push** no celular (lembrete de horário, aviso de atraso ao familiar).
- [ ] Decidir se o app funciona offline (provavelmente não no início).

## Alternativas consideradas

### Hospedagem do PostgreSQL

| Opção | Vantagens | Desvantagens |
|---|---|---|
| Serviço gerenciado (Aiven, PlanetScale, Railway, AWS RDS, Azure, Google Cloud SQL) | Sem manutenção de servidor, backup e alta disponibilidade | Custo; plano gratuito costuma ter limites (verificar antes de escolher) |
| VPS própria (DigitalOcean, Hetzner, Oracle Cloud) com PostgreSQL instalado | Barato e com controle total | Nós cuidamos de backup, atualização e segurança |

### Arquitetura de acesso

- **A. App direto no PostgreSQL:** simples, mas expõe credenciais do banco no app e no dispositivo. **Descartada por segurança.**
- **B. App → API REST → PostgreSQL (recomendada):** credenciais só no servidor, regras de negócio num lugar só, todos os clientes compartilham a mesma lógica.

### Tecnologia do cliente (Android)

- **Flutter:** um código para Android, iOS, Windows, Linux e Mac; boa performance. Exige aprender Dart e reescrever a interface (a Swing não é reaproveitada).
- **React Native / Expo:** JavaScript/TypeScript, celular muito bem servido; desktop é menos maduro.
- **Web responsiva / PWA:** um único código roda em qualquer celular e computador pelo navegador, sem loja de apps; push e recursos nativos são mais limitados. Pode ser empacotada depois (Capacitor, Electron/Tauri).
- **Kotlin nativo (escolhida):** ver decisões abaixo.
- **Java (Spring Boot) para o backend:** mantém a linguagem e a arquitetura atuais, reaproveitando `domain/` e `application/`.

## Direção sugerida (para discutir)

- Backend em **Java + Spring Boot**, reaproveitando `domain/` e `application/`, com o adaptador PostgreSQL e o adaptador REST.
- Cliente **Android em Kotlin** com banco local e sincronização, alarmes locais e push via FCM (a PWA foi descartada, ver "O que o Android + offline implica").
- Hospedar o PostgreSQL em serviço gerenciado gratuito (ver "Hospedagem gratuita").

## Ordem sugerida

1. Definir hospedagem e criar o banco PostgreSQL.
2. Adaptadores PostgreSQL para as portas existentes + migração dos dados do CSV.
3. Conferir que o terminal e a GUI atuais funcionam com o PostgreSQL (validação sem mexer no domínio).
4. Criar a API REST com login/token e autorização.
5. Criar o cliente celular/computador consumindo a API.
6. Notificações push e agendador no servidor.
7. Deploy do backend (HTTPS) e testes com dispositivos reais.

## Definições já tomadas (conversa de 18/09/2026)

- **Custo:** hospedagem **gratuita** por enquanto.
- **Celular:** foco em **Android**, para ter notificação no aparelho. iOS fica de fora por ora.
- **Offline:** o app funciona sem internet. Mudanças feitas offline ficam guardadas no aparelho e só são enviadas ao banco (e, por consequência, aos outros usuários) quando houver conexão. Avisos para outras pessoas também só chegam quando quem envia e quem recebe estiverem online.
- **GUI Swing e terminal continuam existindo** por enquanto.
- **LGPD:** ainda não sabemos como tratar; ver seção própria abaixo.
- **Banco:** **PostgreSQL** no lugar de MySQL (mais opções gratuitas). A troca só afeta o adaptador de persistência.
- **App:** **Kotlin nativo** (Android).
- **Distribuição:** primeiro por **APK direto**; publicar na **Play Store** mais adiante (tem taxa única de cadastro de desenvolvedor).
- **API hibernando (Render) aceita** por enquanto.
- **Vínculo idoso–familiar exige aceite do idoso.** O familiar solicita o vínculo (pelo e-mail do idoso) e ele fica **pendente** até o idoso aceitar no app. Só depois de aceito o familiar vê os dados do idoso. Isso muda o `domain/` (o vínculo ganha um status `PENDENTE / ACEITO / RECUSADO`, ver ADR-0003) e exige internet nos dois lados. Também resolve parte da LGPD (consentimento do idoso).
- **O idoso pode remover um familiar** depois de aceito o vínculo.
- **Pedido de vínculo pendente expira em 24 horas.** Depois disso o familiar precisa solicitar de novo.
- **Conflitos na sincronização:** cada tipo de alteração tem um **peso**; o de maior peso prevalece, e em caso de empate vale o mais recente (ver "Conflitos de edição").
- **Distribuição:** o app será baixado por **APK** (proposta de assinatura, Releases do GitHub e hash SHA-256 aceita).
- **Pesos de conflito:** dependem só do tipo de alteração, não de quem altera. Se o idoso remover um familiar, histórico e medicamentos continuam com o idoso; o familiar só perde o acesso.
- **Autenticação:** proposta aceita (JWT de vida curta + refresh token revogável, ver "Autenticação da API").

### O que o Android + offline implica

- **Banco local no app (SQLite/Room)** com uma fila de alterações pendentes, enviada à API quando voltar a conexão (sincronização).
- **Lembrete de horário do idoso funciona offline:** é agendado localmente no aparelho (`AlarmManager`/`WorkManager`), sem depender de servidor.
- **Aviso ao familiar (remédio tomado ou atrasado) exige internet** nos dois lados: o app do idoso envia a tomada à API, e a API manda **push via Firebase Cloud Messaging (FCM)**, que é gratuito, ao familiar.
- **Conflitos de edição:** definir uma regra simples (ex.: vale a alteração mais recente, `atualizado_em` em cada registro). Tomadas de remédio são só inserções, então quase não geram conflito.
- **Tecnologia do cliente:** **Kotlin nativo**, com melhor integração com alarmes e push. Não reaproveita código do projeto Java, então o app reimplementa as telas (a GUI Swing serve só de referência de design). Sem iOS por ora. PWA foi descartada, pois alarme offline e push confiável no Android são mais frágeis.
- **Desktop:** a GUI Swing e o terminal passam a usar o mesmo banco. Para não colocar a senha do PostgreSQL no computador do usuário, o ideal é que eles também conversem com a API. Enquanto isso, podem usar o adaptador PostgreSQL direto só em desenvolvimento.

## Hospedagem gratuita (pesquisa)

Free tiers mudam com frequência; **conferir os limites atuais no site de cada um antes de criar a conta**. O que a pesquisa mostrou:

**Banco PostgreSQL**
- **Neon (plano gratuito):** 0,5 GB de armazenamento e 100 horas de computação por projeto. A computação suspende após 5 minutos sem uso e acorda em milissegundos na próxima consulta, sem pausar o projeto. É a opção mais indicada para começar.
- **Supabase (plano gratuito):** 500 MB de banco; **pausa o projeto após 1 semana sem atividade** (é preciso restaurar pelo painel), o que é ruim para um app que pode ficar parado. Traz autenticação e storage prontos, que não precisaremos.

**Backend (API Java/Spring Boot)**
- **Render (gratuito):** 512 MB e sem cartão, mas o serviço **hiberna após inatividade** e a primeira requisição pode levar de 10 a 30 s. Aceito, pois o app funciona offline e sincroniza depois.
- **Railway:** só crédito mensal pequeno, que acaba. **Fly.io:** sem plano gratuito para novos usuários.
- **Oracle Cloud (Always Free):** plano B, uma VM sempre ligada que comportaria API e banco juntos, mas exige cartão e mais configuração e segurança.

**Notificações:** Firebase Cloud Messaging é gratuito.

**Escolha atual:** PostgreSQL no **Neon** + API no **Render**.

Fontes: [Neon vs Supabase (2026)](https://toolfreebie.com/supabase-vs-neon/), [Top PostgreSQL free tiers (Koyeb)](https://www.koyeb.com/blog/top-postgresql-database-free-tiers-in-2026), [Free PostgreSQL Hosting: every real option](https://swyftstack.com/blog/free-postgresql-hosting), [Render — free tier 2026](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026), [Fly.io Free Tier 2026](https://www.saaspricepulse.com/blog/flyio-free-tier-2026).

## LGPD (o básico)

A LGPD (Lei 13.709/2018) vale para quem trata dados pessoais. **Dado de saúde, como a lista de remédios de uma pessoa, é dado pessoal sensível** e tem regras mais rígidas. Para um projeto de estudo com poucos usuários o risco é baixo, mas vale já construir do jeito certo. Não é aconselhamento jurídico.

Checklist prático:
- [ ] **Coletar só o necessário:** nome, e-mail, senha, remédios e horários. Nada além disso.
- [ ] **Consentimento claro:** tela de cadastro explicando quais dados são guardados e para quê, com aceite do usuário. Para familiar acompanhar idoso, o idoso precisa autorizar o vínculo.
- [ ] **Política de privacidade** simples, acessível no app.
- [ ] **Direitos do titular:** o usuário poder ver, corrigir (já existe edição) e **excluir a conta e todos os dados**, além de exportá-los.
- [ ] **Segurança:** HTTPS sempre, senha com BCrypt (já feito), banco com senha forte e sem acesso público direto, credenciais fora do repositório, `.gitignore` cobrindo `.env`.
- [ ] **Controle de acesso:** cada pessoa só vê o que lhe cabe (regra já existente, precisa ser aplicada na API).
- [ ] **Não registrar dados sensíveis em logs.**
- [ ] **Backup** e plano simples caso haja vazamento (avisar os usuários).
- [ ] Cuidado com **os CSV atuais e o `medicamentos.csv` modificado** no repositório: não subir dados reais de pessoas para o Git.
- [ ] Ao usar Firebase/FCM, conferir que as notificações não levem o nome do remédio em texto, ou que isso esteja coberto pelo consentimento.

## Detalhamento das dúvidas

### Conflitos de edição (decidido)

Cada alteração tem um **peso**. Quando duas alterações conflitam no mesmo registro (ex.: o app offline editou um medicamento e, antes de sincronizar, outra pessoa o excluiu), **prevalece a de maior peso**. Se tiverem o mesmo peso, **prevalece a mais recente**. A hora que vale é a do servidor ao receber a alteração (`atualizado_em`), não a do relógio do celular, que pode estar errado.

Pesos propostos:

| Alteração | Peso |
|---|---|
| Excluir | 3 |
| Editar | 2 |
| Criar / registrar tomada | 1 |

Consequências:
- **Excluir vence editar**, mesmo que a edição seja mais recente. Uma edição que chegue depois da exclusão é descartada.
- Por isso, exclusão passa a ser **lógica** (registro marcado como excluído, com data), e não apagado na hora. Sem isso o servidor não saberia rejeitar a edição atrasada. Depois de um tempo os registros excluídos podem ser apagados de vez.
- Duas edições no mesmo medicamento têm o mesmo peso: vale a mais recente, **campo a campo** (a edição já é parcial, ADR-0031/0032).
- Tomadas de remédio são apenas inseridas e não geram conflito.
- Quando uma alteração do usuário for descartada por perder o conflito, o app deve **avisar** (ex.: "este medicamento foi excluído por outra pessoa").

### Distribuição segura do APK (aceita)

- **Assinar o APK** com uma chave própria (keystore) e **guardar essa chave e sua senha fora do Git**, com cópia de segurança. Quem perde a chave não consegue mais publicar atualizações do mesmo app.
- **Hospedar o APK num lugar controlado por nós**, como as *Releases* do GitHub, e divulgar só esse link.
- **Publicar o hash SHA-256** do APK junto do link, para quem quiser conferir que o arquivo não foi alterado.
- O usuário precisa permitir "instalar apps de fontes desconhecidas"; explicar isso num passo a passo curto.
- **Manter sempre o mesmo `applicationId`** e aumentar o `versionCode` a cada versão. Assim a migração para a Play Store depois fica simples (lá se publica um *Android App Bundle*, e o Google gerencia a assinatura).

### Autenticação da API (aceita)

Proposta simples e comum:
- Login com e-mail e senha (BCrypt, já existente; mensagem de erro genérica, ADR-0038) devolve dois tokens: **access token JWT de vida curta (~15 min)** e **refresh token de vida longa (~30 dias)**.
- O refresh token fica **guardado no servidor (só o hash)**, pode ser revogado (logout, troca de senha) e é trocado a cada renovação.
- No app, os tokens ficam no **armazenamento criptografado do Android** (`EncryptedSharedPreferences`/Keystore), nunca em texto puro.
- **Offline:** o app continua logado enquanto o refresh token for válido; ao voltar a internet, renova o access token e sincroniza.
- Tudo por **HTTPS** (o Render já fornece). **Limitar tentativas de login** para dificultar força bruta.
- A API descobre o usuário pelo token em cada requisição e aplica as regras de acesso (o papel da `SessaoAtual` no terminal).

## Perguntas em aberto

Nenhuma por enquanto. Próximo passo: começar a implementação pela Frente 1 (banco no Neon e adaptador PostgreSQL).

## Riscos

- Expor o PostgreSQL diretamente na internet (evitar; usar API).
- Vazamento de credenciais no repositório.
- Reescrever a interface em Kotlin e implementar sincronização offline provavelmente será o maior esforço do projeto.
- Hibernação do Render e suspensão do Neon aumentam a latência da primeira requisição.
- Latência e indisponibilidade de rede, que antes não existiam com arquivos locais.

## Data

18/09/2026

## Responsáveis

- Gilvan Pedro
