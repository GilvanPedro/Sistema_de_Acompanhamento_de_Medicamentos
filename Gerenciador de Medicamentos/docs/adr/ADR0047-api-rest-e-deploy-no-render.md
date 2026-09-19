# ADR-0047 — API REST (Spring Boot) com login por token e deploy no Render

## Status

Aceito

## Contexto

Depois de passar a persistência para o PostgreSQL (ADR-0046), a GUI e o terminal falavam direto com o banco, o que obriga cada computador a ter a senha do banco. O app Android planejado (ADR-0045) não pode, e não deve, fazer isso: colocar a credencial do banco dentro de um app instalado em vários aparelhos seria um risco de segurança sério.

Era preciso uma camada online entre os clientes e o banco que:
- guardasse a credencial do banco só no servidor;
- autenticasse cada pessoa e aplicasse as regras de acesso (o idoso vê o que é dele; o familiar vê os idosos que aceitaram o vínculo);
- reaproveitasse as regras de negócio que já existem, sem reescrevê-las;
- rodasse de graça e ficasse acessível pela internet.

## Decisão

### 1. API REST como novo adaptador de entrada

- Pacote `br.com.adapter.in.web`, **dentro do mesmo módulo Maven**, usando os serviços de `application/` e as portas de `domain/` sem alterá-los. É mais um adaptador de entrada da arquitetura hexagonal, ao lado de `console` e `gui`.
- **Spring Boot 3.5.16** (última da linha 3.x; a 4.x mudou bastante e foi evitada). Entra por importação do BOM (`spring-boot-dependencies`), **sem** o `spring-boot-starter-parent`, para a GUI e o terminal continuarem rodando com `exec:java` como antes. Por isso foi preciso ligar o `-parameters` do compilador (`maven.compiler.parameters`) e deixar explícito o nome nos `@PathVariable`.
- **Montagem dos serviços** em `ServicosConfig`, a partir das portas. Em produção, `AdaptadoresConfig` fornece os adaptadores PostgreSQL; nos testes (perfil `test`), entram versões em memória. A API não usa o `AppConfig`, cujos campos estáticos escolhem CSV ou PostgreSQL.
- **A API só roda com PostgreSQL.** Sem `DATABASE_URL` ela recusa iniciar e diz o motivo.
- **Configuração sensível** (`Ambiente`): lê a variável de ambiente e, se não existir, o arquivo `.env` na pasta atual ou na pasta acima. O `.env` está no `.gitignore`, e o `.env.example` traz valores de mentira.

### 2. Autenticação

- **Login** por e-mail e senha (BCrypt, mensagem genérica) devolve dois tokens:
  - **Acesso:** JWT assinado com HMAC-SHA256 (biblioteca jjwt 0.12.6), válido por **15 minutos**. O segredo vem de `JWT_SECRET` (mínimo de 32 caracteres; a API não sobe sem ele).
  - **Renovação:** 32 bytes aleatórios, válido por **30 dias**. O banco guarda só o **hash SHA-256**, na tabela `refresh_token` (`V2__criar_refresh_token.sql`).
- **Rotação com detecção de reuso:** cada renovação invalida o token usado e emite outro. Se um token já usado aparecer de novo, todas as sessões da pessoa são revogadas. A revogação é atômica (`UPDATE ... WHERE revogado_em IS NULL`), o que impede o uso simultâneo do mesmo token.
- **Encerramento de sessões:** sair revoga o token de renovação; trocar a senha ou excluir a conta revoga todos.
- **Exclusão de conta (LGPD):** `DELETE /me` (exige a senha atual, ver ADR-0048) apaga de verdade os dados de saúde da pessoa (medicamentos e histórico) e os vínculos, e **anonimiza** o cadastro (nome "Conta excluída", e-mail `excluido-<id>@excluido.invalid`, senha inutilizada). A linha do usuário permanece só como registro anônimo marcado como excluído, para os ids continuarem válidos e o app saber que a conta deixou de existir. Tudo numa transação. Excluir um medicamento também apaga o nome dele (a linha fica só como marca de exclusão, para a sincronização).
- **Limites de tentativas** (HTTP 429), detalhados no ADR-0048: senhas erradas por e-mail e IP, falhas por IP, contas novas por IP e senha atual errada.
- **Sem Spring Security:** a API é sem estado e usa `Authorization: Bearer`, sem cookies, então não há CSRF. Um filtro simples (`FiltroAutenticacao`) valida o token em tudo que está em `/api/v1`, exceto `/auth/**` e `/saude`.

### 3. Autorização

A classe `Acesso` cumpre o papel que a `SessaoAtual` tem no terminal. **A cada requisição, o usuário é buscado no banco** (não se confia só no conteúdo do token), então uma conta excluída perde o acesso na hora.
- O idoso acessa só os próprios dados.
- O familiar acessa só os idosos com vínculo **aceito** (o pedido pendente não dá acesso).
- Só o idoso registra que tomou um remédio, e só os próprios.

### 4. Rotas (`/api/v1`, JSON)

| Grupo | Rotas |
|---|---|
| Autenticação | `POST auth/registro`, `auth/login`, `auth/renovar`, `auth/sair` |
| Conta | `GET/PATCH/DELETE me` (trocar senha ou e-mail e excluir exigem a senha atual), `GET me/idosos`, `GET me/familiares` |
| Medicamentos | `GET/POST idosos/{id}/medicamentos`, `PATCH/DELETE medicamentos/{id}` |
| Tomadas e avisos | `POST medicamentos/{id}/tomadas`, `GET idosos/{id}/historico`, `GET idosos/{id}/notificacoes` |
| Vínculos | `POST/GET vinculos/pedidos`, `POST vinculos/pedidos/{familiarId}/aceitar\|recusar`, `POST me/familiares`, `DELETE me/familiares/{familiarId}` |
| Saúde | `GET saude` (pública, sem dados) |

- **Erros** em `{"erro": "mensagem"}`, sem stack trace nem detalhe técnico. As exceções do domínio viram 400, 401, 403, 404 e 429; o inesperado vira um 500 genérico, com o detalhe apenas no log.
- **Fuso horário:** o servidor roda em UTC, mas os horários dos remédios são os do relógio de quem toma. A API define `America/Sao_Paulo` como fuso padrão da JVM (configurável por `APP_TIMEZONE`).

### 5. Deploy no Render

- **Contêiner Docker**, porque o Render não tem Java nativo. `Dockerfile` na raiz do repositório, em dois estágios: compila com Maven (imagem `maven:3.9-eclipse-temurin-17`) e roda o `.jar` executável (`spring-boot-maven-plugin`, meta `repackage`) numa imagem `eclipse-temurin:17-jre`, com usuário sem privilégios.
- **Memória:** o plano gratuito tem 512 MB; a JVM é limitada (`MaxRAMPercentage=70`, coletor serial). O uso medido localmente foi de cerca de 267 MB.
- **`render.yaml`** descreve o serviço (`runtime: docker`, plano gratuito, verificação de saúde em `/api/v1/saude`, `autoDeploy`). `DATABASE_URL` e `JWT_SECRET` ficam com `sync: false`, ou seja, são digitados no painel e nunca vão para o Git.
- **Endereço em produção:** `https://sistema-de-acompanhamento-de-medicamentos.onrender.com`.

## Alternativas consideradas

### Alternativa 1 — App direto no banco
**Vantagens:** nenhuma camada a mais.
**Desvantagens:** a credencial do banco iria dentro de cada app e computador. **Descartada por segurança.**

### Alternativa 2 — Spring Security
**Vantagens:** padrão da indústria, com muita coisa pronta.
**Desvantagens:** configuração pesada para uma API pequena e sem estado. Foi preferido um filtro simples com JWT, que é fácil de ler e testar. Pode ser reavaliado se a API crescer.

### Alternativa 3 — Sessão com cookie no lugar de tokens
**Vantagens:** simples num site.
**Desvantagens:** cliente é um app Android, e o esquema com token de acesso curto e renovação é mais adequado, além de permitir revogar por aparelho.

### Alternativa 4 — API em módulo ou repositório separado
**Vantagens:** separação física.
**Desvantagens:** duplicaria ou exigiria publicar `domain/` e `application/`. Mantendo no mesmo módulo, a API é só mais um adaptador.

### Alternativa 5 — VM Oracle Always Free
**Vantagens:** sempre ligada, sem hibernação.
**Desvantagens:** exige cartão e mais trabalho de configuração e segurança. Fica como plano B.

## Justificativa

- **Segurança:** a credencial do banco fica só no servidor; senhas com BCrypt; tokens curtos, revogáveis e com detecção de reuso; nada de segredo no Git.
- **Manutenibilidade e acoplamento:** a arquitetura hexagonal permitiu criar a API sem mexer nas regras de negócio.
- **Custo:** Render e Neon em planos gratuitos.
- **Testabilidade:** como a API monta os serviços a partir de portas, dá para testá-la com adaptadores em memória, sem banco.

## Consequências

**Positivas**
- O app Android (e qualquer outro cliente) passa a usar a mesma API, com as mesmas regras de acesso.
- Primeiros **testes automatizados** do projeto: 13 testes de integração da API (`ApiTest`) com portas em memória. Cobrem autenticação (sem token, token inválido, login errado, limite de tentativas), permissões (familiar com e sem vínculo, pedido pendente, recusado, removido, intruso), cadastro e edição parcial de medicamentos, tomada (só o idoso, uma vez por dia), rotação e reuso de token, troca de senha e exclusão de conta. Eles pegaram um erro real antes do deploy (falta do `-parameters`).
- **Verificação da exclusão (LGPD):** o SQL de anonimização foi verificado contra o banco real, com dados de teste que foram removidos em seguida: cadastro anonimizado, medicamentos, histórico e vínculos apagados, o familiar do vínculo intacto, o idoso excluído não é achado por e-mail nem por id, e excluir duas vezes é inofensivo.
- **Verificação no ar:** com uma conta de teste (depois excluída), foram exercitados no Render `saude`, cadastro, login, `me`, cadastro e listagem de medicamento, avisos, histórico, renovação, reuso de token antigo (401) e exclusão de conta.

**Negativas**
- **Hibernação:** no plano gratuito, o serviço dorme após cerca de 15 minutos sem uso, e a primeira requisição seguinte pode levar de 30 a 60 segundos (a Neon também suspende o banco). Aceito, pois o app funcionará offline.
- **Os testes automatizados não cobrem o SQL** dos adaptadores PostgreSQL nem o schema (usam portas em memória); isso foi verificado manualmente.
- **Limite de tentativas em memória:** some ao reiniciar e não é compartilhado entre instâncias. Serve para uma instância só.
- **Tokens de renovação expirados não são apagados** da tabela `refresh_token`; falta uma limpeza periódica.
- **A linha anônima do usuário excluído permanece** no banco (sem dados pessoais). Se um dia for necessário apagá-la de vez, dá para fazê-lo depois que o app não precisar mais saber da exclusão.
- Trocar o `JWT_SECRET` desloga todo mundo.
- A GUI e o terminal ainda falam direto com o banco; migrá-los para a API é um passo futuro.
- Sem CORS (o cliente é um app Android, não um navegador).

## Impactos

- `pom.xml`: BOM do Spring Boot, `spring-boot-starter-web`, jjwt, `spring-boot-starter-test`, surefire 3.2.5, plugin de empacotamento e `maven.compiler.parameters`.
- Novo pacote `adapter/in/web` (aplicação, configuração, autenticação, tratamento de erros, DTOs e controladores) e testes em `src/test`.
- `config/Ambiente` (leitura de configuração), usado também por `ConexaoPostgres`.
- `db/migration/V2__criar_refresh_token.sql`.
- Novos arquivos na raiz: `Dockerfile`, `render.yaml`, `.dockerignore`, `.env.example`.

## Implementação

1. Executar o `V2__criar_refresh_token.sql` no SQL Editor da Neon.
2. Definir `DATABASE_URL` e `JWT_SECRET` (no `.env`, localmente; no painel, no Render).
3. Localmente: iniciar `br.com.adapter.in.web.ApiApp` (porta 8080).
4. Produção: conectar o repositório no Render (Blueprint ou serviço Docker); ele constrói o `Dockerfile` e passa a atender em HTTPS.

## Observações

- Próximos passos (ADR-0045): app Android em Kotlin com uso offline e sincronização; notificações push (Firebase); distribuição por APK.
- Pendências desta etapa: limpar tokens de renovação expirados e migrar a GUI e o terminal para a API. A anonimização na exclusão de conta foi resolvida em seguida (ver seção 2). O consentimento explícito no cadastro, a política de privacidade e a exportação dos dados do titular (checklist do ADR-0045) seguem para o app.

## Data

19/09/2026

## Responsáveis

- Gilvan Pedro
