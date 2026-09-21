# ADR-0054 — Banners de anúncios no app Android

## Status

Aceito (código pronto e testado; a conferência no celular depende de publicar o servidor com o catálogo)

## Contexto

Queremos ter espaços de anúncio no app, em forma de banner, escolhidos ao acaso de um conjunto salvo online, e que a pessoa consiga fechar sem dificuldade. O público principal são idosos, então o risco de um toque sem querer num anúncio é real: o banner não pode atrapalhar nem parecer parte da tela.

## Decisão

### 1. Catálogo próprio, no servidor

Os banners ficam num arquivo JSON público do servidor (`/anuncios/anuncios.json`), com as imagens na mesma pasta (`src/main/resources/static/anuncios/`):

```json
{ "anuncios": [
  { "id": "exemplo-1", "imagem": "banner-exemplo-1.png", "link": null, "texto": "Descrição para leitores de tela" }
] }
```

- **Trocar os anúncios não exige atualizar o app**: basta mudar o JSON e as imagens e publicar o servidor.
- `imagem` pode ser relativa ao JSON ou um endereço `https` completo (permite hospedar em outro lugar depois). `link` é opcional; sem link, o banner não é clicável.
- Um teste do servidor garante que a lista existe, que cada imagem relativa existe de verdade, que os `id` não se repetem e que todo link é `https` ou `mailto`.

### 2. Como o app usa o catálogo (`CatalogoDeAnuncios`)

- Baixa a lista (timeouts curtos), **guarda uma cópia** no aparelho (aparece também sem internet) e escolhe **um banner ao acaso** cada vez que um local de anúncio aparece na tela.
- Imagens com cache em memória e em disco (válido por 24 h; sem rede, a cópia velha ainda serve), limite de 3 MB por imagem e redução de imagens muito grandes.
- **Sem banner disponível, não aparece nada** e o espaço não é reservado: a tela fica como sempre foi.
- **Segurança:** imagens só por `https`. Links só `https` ou `mailto:` (este só com destinatário, `subject` e `body`: sem `cc`, `bcc` nem `attach`); nada de `tel:`, `intent:`, `file:`... Um link `mailto:` abre o app de e-mail já com o destinatário e o assunto preenchidos (é como os banners "Anuncie aqui" levam ao contato do desenvolvedor). Um cliente HTTP próprio, **sem o token de login**: nenhum dado da conta vai nesses pedidos.
- Sem rastreamento: o app não mede nem envia impressões, toques ou qualquer dado do usuário para os banners.

### 3. Como o banner se comporta

- Sempre com o rótulo **"Anúncio"**, dentro de um cartão com borda, para não parecer conteúdo do app.
- Botão **"Fechar ✕"** grande (altura mínima de 48 dp), no canto do cartão, com descrição para leitores de tela. Fechar esconde o banner **naquele lugar** enquanto o app estiver aberto; ao abrir o app de novo ele pode voltar.
- Tocar na imagem abre o link no navegador (só se o banner tiver link).

### 4. Onde aparecem

Os banners de baixo são sempre o **último elemento da tela** (rolando, se preciso), nunca no meio de botões, para não serem tocados por engano.

| Local | Posição |
|---|---|
| Tela inicial, login e cadastro | fim da tela |
| Início depois do login (idoso e familiar) | **topo**, antes do "Olá, ..." e **fim** da tela |
| Tela de um idoso (para ver o histórico ou mudar os remédios) | fim da tela |
| Lista de remédios de um idoso | fim da tela |

Nas telas de formulário de remédio, de tomada, do histórico, de vínculos e de "Meus dados" **não há banner**, de propósito.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Rede de anúncios (AdMob) | Exige conta e aprovação, usa identificador de publicidade e personalização (pediria consentimento específico na LGPD e mudaria a política de privacidade e o formulário da Play), e é mais difícil controlar o que aparece ao lado de dados de saúde |
| Catálogo num banco de dados, com painel de administração | Mais código e mais superfície de ataque para poucos banners |
| Baixar as imagens sempre (sem cache) | Gasta dados e demora a cada tela |

## Consequências

- **Política de privacidade:** ganhou uma frase dizendo que os banners são escolhidos ao acaso, não usam dados pessoais nem os remédios, podem ser fechados e levam ao site do anunciante ao toque. A versão do texto **não foi alterada** (1.0), porque nenhum tratamento de dado novo foi criado; se preferir que todos aceitem de novo, é só subir a versão (ADR-0052).
- **Play Store:** o app passa a **conter anúncios** e isso precisa ser declarado no console.
- **Sem métricas:** não há contagem de exibições nem de toques. Se um anunciante pedir isso, é uma decisão nova (e de privacidade).
- Os banners atuais são **imagens de exemplo**, para serem trocadas pelas reais.
- A interface gráfica do computador não tem banners.

## Como adicionar ou trocar um banner

1. Colocar a imagem (PNG ou JPEG, proporção parecida com 3,2:1, por exemplo 640×200) em `Gerenciador de Medicamentos/src/main/resources/static/anuncios/`.
2. Acrescentar uma entrada em `anuncios.json` (`id` único, `imagem`, `link` opcional em `https`, `texto` descrevendo o banner).
3. Publicar o servidor. Os celulares pegam o catálogo novo na próxima abertura do app (a imagem nova, em até 24 h).
