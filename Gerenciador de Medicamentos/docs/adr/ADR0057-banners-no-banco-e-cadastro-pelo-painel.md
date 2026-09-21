# ADR-0057 — Banners no banco de dados e cadastro pelo painel

## Status

Aceito (código pronto e testado; falta a migração V9 e o deploy)

## Contexto

Os banners eram arquivos do projeto (`static/anuncios/`, um JSON e as imagens), publicados junto com o servidor a partir do GitHub (ADR-0054). Isso tinha dois problemas:

1. **Os banners dos patrocinadores ficavam públicos no repositório**, inclusive no histórico, e o projeto é de código aberto.
2. **Trocar um patrocinador exigia mexer no código**: salvar o arquivo, commit, push e deploy.

Simplesmente tirar a pasta do Git derrubaria os banners em produção, porque o Render só publica o que está no GitHub.

## Decisão

### 1. Os banners moram no banco (Neon)

Tabela `banner` (migração **V9**): `id`, `empresa`, `texto`, `link`, `peso`, `tipo_imagem`, `imagem` (os bytes, em `BYTEA`) e `atualizado_em`. Imagens pequenas (até 1 MB, o ideal é uns 300 KB) cabem bem no plano gratuito do Neon.

### 2. Cadastro pelo painel (`/painel/banners`)

Com a mesma senha do painel (`PAINEL_SENHA`, ADR-0055): **adicionar** (empresa, descrição, link, peso e imagem), **editar** (inclusive trocar a imagem), **pausar/ativar** e **remover**. Sem commit e sem deploy; a mudança vale em poucos minutos (o servidor guarda a lista por 30 s e 60 s para os pesos, e o app renova a lista a cada 15 min).

O servidor **não confia no navegador**: confere tudo.

- **Imagem:** só PNG ou JPEG, reconhecidos pelo **conteúdo** (não pelo nome nem pelo tipo informado); até 1 MB; de 300 a 4000 px de largura e de 60 a 2000 de altura; **formato de banner** (largura de 2 a 6 vezes a altura, o ideal é 1280×400). As medidas são lidas só do cabeçalho, para uma imagem "bomba" não estourar a memória. SVG e GIF são recusados (SVG poderia carregar script).
- **Link:** vazio, ou `https://` com endereço válido, ou `mailto:` só com destinatário, assunto e mensagem (sem `cc`, `bcc` nem `attach`). Nada de `javascript:`, `http:` etc.
- **Peso:** de 0 (pausado) a 100. **Empresa:** 1 a 120 caracteres. No máximo 50 banners.
- A **identificação** do banner vem do nome da empresa ("Padaria do Zé" vira `padaria-do-ze`), com número no fim se já existir.
- **Proteção do formulário (CSRF):** cada formulário leva um código escondido (derivado da senha do painel), e o servidor confere também o cabeçalho `Origin`. Como o navegador reenvia a senha do painel sozinho, sem isso outro site poderia mandar um formulário em nome de quem está logado.
- Tudo que vem de fora é escapado nas páginas.

### 3. Como o app e as versões antigas recebem os banners

- `GET /api/v1/anuncios` (ADR-0056) agora lê do banco. As imagens saem de `GET /anuncios/{id}.png|jpg`, com `?v=` (o momento da última alteração) para os celulares buscarem a imagem de novo quando ela mudar, apesar do cache de 24 h.
- `GET /anuncios/anuncios.json`, o endereço que as **versões antigas** do app usam, continua existindo, agora gerado do banco com imagens de endereço completo. **O app não precisou mudar.**

### 4. O que vai para o GitHub: só um exemplo

`static/anuncios-exemplo/` (um `anuncios.json` com **um** banner de exemplo, "Anuncie aqui", e a imagem). Ele só vale **enquanto não houver nenhum banner cadastrado** (projeto recém-clonado, testes): assim quem clona o repositório vê o recurso funcionando. A pasta antiga `static/anuncios/` foi tirada do Git e colocada no `.gitignore`, para nenhum banner de verdade voltar por engano.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Repositório privado só dos banners (servidor lê com token) | Uma peça a mais para manter (token e repositório) e mexer em banner continua sendo commit |
| Tornar o repositório inteiro privado | Muda a natureza do projeto (aberto) e não resolve o segundo problema (trocar banner exige commit) |
| Armazenamento de arquivos externo (S3, Cloudinary) | Conta e chave a mais, para poucas imagens pequenas que já cabem no banco |
| Só ignorar a pasta no Git | O Render publicaria sem nenhum banner |

## Consequências

- O repositório **não contém mais** banners de patrocinadores, só o exemplo. **O histórico antigo do Git ainda tem os arquivos que já foram commitados** (inclusive o código de afiliado): removê-los de vez exigiria reescrever o histórico (`git filter-repo`), o que foi deixado de fora de propósito.
- **Depois do deploy os banners passam a vir do banco.** Enquanto a tabela estiver vazia, o app mostra só o banner de exemplo; é preciso cadastrar os banners de verdade pelo painel (uma cópia dos antigos fica em `~/Documentos/anuncios-cuidamed-originais`).
- As imagens dos banners passam a fazer parte do banco e das cópias de segurança dele.
- Continua valendo: quem acessa o painel com a senha pode cadastrar qualquer banner. Guarde a `PAINEL_SENHA` como a chave do Render.
- Os relatórios (ADR-0055) mostram o nome cadastrado da empresa; banner removido continua nos números do mês como "fora do catálogo".

## Como ligar

1. Rodar `V9__criar_banner.sql` na Neon.
2. Publicar o servidor (a `PAINEL_SENHA` do ADR-0055 já serve).
3. Abrir `https://<servidor>/painel/banners` e adicionar os banners.
