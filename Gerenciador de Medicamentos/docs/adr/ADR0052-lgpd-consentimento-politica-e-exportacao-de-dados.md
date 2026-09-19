# ADR-0052 — LGPD: consentimento, política de privacidade e exportação dos dados

## Status

Aceito

## Contexto

O CuidaMed guarda **dados pessoais sensíveis**: quais remédios uma pessoa toma, quando e se tomou (dados de saúde, LGPD art. 5º, II). Para tratar esse tipo de dado a lei exige, entre as bases legais possíveis, o **consentimento específico e destacado** (art. 11, I), e garante ao titular direitos como acesso, correção, eliminação e retirada do consentimento (art. 18). O sistema já tinha a exclusão de conta com anonimização (ADR-0047/0048); faltavam o consentimento, a política e a cópia dos dados.

## Decisão

### 1. Política de privacidade versionada

- Um único texto (versão **1.0**, em vigor desde 19/09/2026) explica: quem é o responsável, quais dados são guardados, para quê, a base legal, com quem são compartilhados (familiares vinculados; Render, Neon e Firebase como operadores; possível transferência internacional), por quanto tempo, os direitos do titular, como os dados são protegidos, a idade mínima (18 anos) e como o texto muda.
- O texto é escrito **uma vez** e gerado nos dois lugares: a página pública `/politica-de-privacidade.html` (servida pela API, útil também para a Play Store) e a tela do app (`TextoDaPolitica.kt`). Assim os dois nunca divergem.
- A versão atual é uma constante no servidor (`PoliticaDePrivacidade.VERSAO_ATUAL`) e outra no app e na GUI. **Mudou o texto de verdade? Sobe a versão nos três lugares**: todo mundo é chamado a aceitar de novo.
- Contato do titular: o e-mail informado na política.

### 2. Registro do aceite

Tabela `consentimento (usuario_id, versao, aceito_em)` (migração V7), uma linha por versão aceita.

- **Cadastro:** o corpo traz `aceitouPolitica: true` e `versaoPolitica`. O servidor recusa (400) sem o aceite ou com uma versão diferente da atual, para ninguém aceitar um texto que não viu. O aceite é gravado na criação da conta.
- **Contas anteriores à política:** `GET /me/consentimento` diz qual é a versão atual e qual a conta já aceitou; `POST /me/consentimento` grava o aceite (só da versão atual).

### 3. Onde o aceite é exigido

- **App Android:** caixa "Li e aceito as políticas de privacidade" no cadastro (com botão para ler o texto). Quem já tinha conta vê a tela "Antes de continuar", com o texto, "Li e aceito" e "Não aceito: sair da conta". O app guarda no aparelho que a versão já foi conferida (por conta), para não perguntar ao servidor toda vez. **Sem internet ou com servidor lento (mais de 8 s), o app abre normalmente** e confere na próxima vez: não deixar uma pessoa idosa sem os lembretes de remédio por causa de um aceite é a escolha mais segura.
- **Interface gráfica:** mesma caixa no cadastro, e uma tela de aceite depois do login. Aqui, se não der para conferir, mostra o erro com "Tentar de novo" (a GUI sempre depende da API).

### 4. Direitos do titular

| Direito | Como se exerce |
|---|---|
| Acesso e cópia | **"Baixar meus dados"** em "Meus dados": `POST /me/exportar`, com a senha atual. Devolve um JSON com conta, aceites, remédios e histórico (idoso) ou os idosos que acompanha (familiar). |
| Correção | Editar nome, e-mail e senha em "Meus dados" |
| Eliminação e retirada do consentimento | **"Excluir minha conta"** (pede a senha): apaga remédios, histórico, vínculos, tokens de aparelho e aceites, e o cadastro perde nome e e-mail |
| Informação sobre compartilhamento | A própria política |

A exportação **só traz dados da própria pessoa**: de outras pessoas vinculadas vai apenas o nome, e **nunca** senha nem hash. Pede a senha (com o mesmo limite de tentativas de outras ações sensíveis) para que quem pegue um celular ou computador desbloqueado não baixe os dados.

### 5. O push não leva dado pessoal

Já decidido no ADR-0050: o aviso enviado pelo Firebase só diz "há novidade". Nome, remédio e horário nunca passam pelo Google.

## Consequências

- Novas contas só existem com aceite registrado; contas antigas aceitam na primeira abertura depois da atualização.
- A GUI e o app têm o mesmo conjunto de direitos.
- **Limites conhecidos:**
  - O texto da política foi redigido junto com o desenvolvedor e **não passou por revisão jurídica**. Recomenda-se essa revisão antes de distribuir o app para o público.
  - A idade mínima (18 anos) é declarada, não verificada tecnicamente.
  - Não há um encarregado (DPO) formal: o contato é o e-mail do responsável.
  - Ao excluir a conta, ficam alguns resíduos sem dado pessoal: a linha anonimizada em `usuario`, o hash de tokens de renovação já revogados e chaves de idempotência (só ids) até a limpeza por expiração. A **limpeza periódica dessas linhas** ainda está pendente.
  - Cópias de segurança dos provedores (Neon) podem manter dados por mais algum tempo.

## Como ligar

Rodar `V7__criar_consentimento.sql` na Neon antes de publicar o servidor.
