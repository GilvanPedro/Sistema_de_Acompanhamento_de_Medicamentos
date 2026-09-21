# ADR-0058 — Vídeo curto nos banners

## Status

Aceito (código pronto e testado no servidor e no app; a conferência do vídeo tocando no celular depende de publicar o servidor com a migração V10)

## Contexto

Os banners eram só imagem. Um vídeo curto chama mais atenção e vale mais para o anunciante, mas num app usado por idosos ele precisa ser discreto: sem som, sem gastar a internet do celular, sem atrapalhar quem está ouvindo música e sem prejudicar quem desligou as animações.

## Decisão

### 1. O banner ganha um vídeo opcional; a imagem continua obrigatória

Um banner tem **sempre a imagem** (ADR-0057) e, se quiser, também um **vídeo MP4**. A imagem é o plano B em todo caso: aparece enquanto o vídeo carrega, se o vídeo falhar, na internet do celular (dados móveis) e para quem desligou as animações do Android.

### 2. Cadastro pelo painel (`/painel/banners`)

O formulário ganhou o campo "Vídeo curto" (e, na edição, uma caixa "Remover o vídeo atual" e a prévia do vídeo). O servidor **confere o arquivo pelo conteúdo**, lendo só o cabeçalho do MP4 (as "caixas" do arquivo), sem biblioteca e sem decodificar nada:

- é um MP4 (a assinatura `ftyp`), com uma faixa de vídeo **H.264** (`avc1`/`avc3`), o codec que todo Android toca; H.265, VP9 etc. são recusados com o nome do codec encontrado;
- **1 a 15 segundos** de duração e até **4 MB** (o ideal é uns 2 MB);
- de 320 a 1920 px de largura e de 60 a 1080 de altura, com **formato de banner** (largura de 2 a 6 vezes a altura);
- **formato parecido com o da imagem** (até 20% de diferença de proporção), senão o vídeo seria cortado no lugar da imagem. Trocar só a imagem de um banner que já tem vídeo também confere isso.
- No máximo **15 banners com vídeo**, por causa do espaço do banco gratuito.

### 3. Onde o vídeo fica e como é entregue

O vídeo fica no banco junto com o banner (migração **V10**: colunas `video` e `duracao_video_ms`). Sai em `GET /anuncios/{id}.mp4` com **suporte a pedidos parciais (Range)**, que os players usam para começar a tocar sem baixar tudo. A lista do app (`/api/v1/anuncios`) ganhou o campo `video` (endereço completo, com `?v=` para renovar o cache quando muda). Versões antigas do app ignoram o campo e mostram a imagem.

### 4. Como o app toca (Media3/ExoPlayer)

- Toca **sem som** e **sem pedir o foco de áudio**: música ou ligação em andamento não são interrompidas.
- Em **repetição**, e **só enquanto o banner está pelo menos 50% visível** (pausa ao sair da tela).
- O vídeo só **começa a ser baixado quando o banner chega à tela** (um banner no fim de uma tela longa que a pessoa não rolou até ele não gasta dados).
- **Não toca em dados móveis** (rede "medida") **nem** para quem desligou as animações do Android: nesses casos só a imagem aparece. Isso é decidido na hora em que o banner aparece.
- Guarda os vídeos já vistos no aparelho (até 40 MB, os mais antigos saem primeiro), então repetir ou voltar à tela não baixa de novo.
- O vídeo fica **transparente até o primeiro quadro aparecer** (ou se der erro), deixando a imagem à mostra por baixo; o toque no banner vale como sempre (abre o link) e as contagens do painel (ADR-0055) não mudam: a exibição conta pela mesma regra de visibilidade, com ou sem vídeo.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Guardar os vídeos num serviço de arquivos externo | Conta e chave a mais, para poucos vídeos pequenos que cabem no banco |
| GIF animado | Muito maior que um MP4 para a mesma qualidade, sem controle de repetição e pausa |
| Vídeo com som | Inadequado para um app usado por idosos, muitas vezes em lugares silenciosos, e exigiria o foco de áudio |
| Tocar em qualquer rede | Gastaria os dados móveis de quem tem um plano pequeno |
| `VideoView` do Android | Pede o foco de áudio ao começar (interromperia a música) e tem menos controle do que o ExoPlayer |

## Consequências

- O APK ficou maior (a biblioteca de vídeo do Android, o Media3, acrescenta alguns MB).
- O banco guarda vídeos: com o teto de 15 vídeos de 4 MB, são no máximo 60 MB, dentro do plano gratuito.
- Um vídeo só é exibido nas versões **novas** do app; as antigas mostram a imagem.
- O primeiro vídeo de cada banner precisa ser baixado (até 4 MB) em Wi-Fi; depois fica no cache do aparelho.
- O vídeo passa só pelos nossos servidores; nenhum dado da pessoa vai junto no pedido.

## Como ligar

1. Rodar `V10__adicionar_video_ao_banner.sql` na Neon (depois da V9).
2. Publicar o servidor e o app novo.
3. Em `/painel/banners`, adicionar ou editar um banner e escolher o vídeo.

Para preparar o arquivo: `ffmpeg -i original.mp4 -vf scale=1280:400 -c:v libx264 -crf 30 -an -movflags +faststart banner.mp4`.
