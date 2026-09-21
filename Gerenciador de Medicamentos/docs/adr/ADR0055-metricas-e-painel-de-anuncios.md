# ADR-0055 — Métricas dos banners e painel de anúncios

## Status

Aceito (código pronto e testado; falta a migração V8, a senha do painel e o deploy)

## Contexto

Para vender espaço de anúncio é preciso mostrar resultados: quantas vezes cada banner apareceu, quantas pessoas tocaram, e quem usa o app (idosos ou familiares). Queremos um relatório mensal geral (só para o dono do app) e um relatório de cada banner que possa ser enviado à empresa dona dele, sem que ela veja os concorrentes. Isso precisa respeitar a LGPD e a política de privacidade (ADR-0052 e ADR-0054): sem rastrear pessoas.

## Decisão

### 1. Contagens anônimas e agregadas

O app conta dois eventos por banner:

- **Exibição:** pelo menos **50% do banner visível por pelo menos 1 segundo** (o critério comum para anúncio de tela). Um banner no fim de uma tela longa só conta se a pessoa rolou até ele.
- **Clique:** toque no banner (só banners com link).

Cada evento é somado numa contagem por **dia, banner, posição na tela e perfil** (`IDOSO`, `FAMILIAR` ou `VISITANTE`, quando não há login). **Não se guarda quem viu ou tocou**: nem conta, nem e-mail, nem identificador do aparelho, nem endereço de IP (o IP só serve, na memória, para limitar abusos). Por isso os números não medem "pessoas diferentes alcançadas", só quantas vezes o banner apareceu. Isso é dito nos próprios relatórios.

Tabela `metrica_anuncio (dia, anuncio_id, posicao, perfil, exibicoes, cliques)`, migração **V8**. Cada envio faz `INSERT ... ON CONFLICT DO UPDATE` somando.

### 2. Como o app envia

`MetricasDeAnuncios` guarda as contagens no aparelho (elas sobrevivem se o app fechar) e as envia **em lote** para `POST /api/v1/anuncios/eventos`, cerca de 20 segundos depois do último evento, quando o app vai para o fundo e na abertura seguinte se sobrou algo. Sem internet, as contagens esperam e vão junto no próximo envio (com o dia em que aconteceram). Há um teto de contagens acumuladas no aparelho.

O servidor não exige login (a tela de entrada também tem banner) e **ignora** tudo que for inválido: banner que não está no catálogo, posição desconhecida, quantidade fora de 1 a 100, tipo desconhecido. Perfil estranho vira `VISITANTE`; dia muito antigo ou futuro vale hoje. Há limite de 120 envios por hora por IP, e cada lote aceita no máximo 200 eventos.

### 3. Painel online (sem bibliotecas, só HTML)

| Endereço | Quem acessa | O que mostra |
|---|---|---|
| `/painel` | dono do app (senha) | Relatório **geral** do mês: totais, gráficos por dia, tabela por banner, perfil (idosos/familiares/visitantes) e posição na tela. Navega entre meses |
| `/painel/anuncio/{id}` | dono do app (senha) | Relatório de **um** banner, com o link privado para a empresa |
| `/painel/exportar.csv?mes=AAAA-MM` | dono do app (senha) | Planilha do mês (uma linha por contagem) |
| `/relatorio/{id}/{código}` | quem tiver o link | Relatório de **só aquele banner**, sem senha |

- **Senha do painel:** variável `PAINEL_SENHA` (mínimo de 12 caracteres), por HTTP Basic (o navegador pede a senha; o usuário pode ser qualquer um). Sem ela, o painel e os relatórios ficam **desligados (404)**. Erros de senha são limitados por IP (5 em 15 minutos).
- **Link para a empresa:** o `{código}` é um HMAC-SHA256 do id do banner com a senha do painel, fixo e impossível de adivinhar. O código de um banner **não abre outro**. Como depende da senha, **trocar a senha invalida os links já enviados**.
- Os relatórios têm versão para impressão (imprimir ou salvar em PDF pelo navegador), não são indexados por buscadores e nunca são guardados em cache. Todo texto que vem de fora é escapado, e as células do CSV são protegidas contra fórmulas de planilha.
- O "documento por mês" é o próprio relatório de cada mês (calculado na hora a partir das contagens, sem gerar arquivos), acessível a qualquer momento.

### 4. Catálogo

O `anuncios.json` ganhou o campo opcional `empresa` (nome mostrado nos relatórios; sem ele aparece o `id`). O app ignora o campo.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Google Analytics / Firebase Analytics | Coleta identificadores do aparelho e envia dados a terceiros: contraria a política e exigiria consentimento |
| Contar pessoas únicas (alcance) | Exigiria um identificador por aparelho ou por conta: dado pessoal |
| Guardar cada evento com data e hora | Mais volume no banco gratuito e mais próximo de rastreamento, sem ganho para o anunciante |
| Documento no Google Docs/Planilhas | Exigiria credenciais e integração de terceiros; o painel próprio resolve com menos superfície |
| Gerar PDF no servidor | Dependência pesada; imprimir/salvar em PDF pelo navegador basta |

## Consequências

- **Política de privacidade:** ganhou uma frase dizendo que o app conta, sem identificar ninguém, exibições, toques e o perfil (idoso ou familiar). A versão do texto não foi alterada (1.0), porque as contagens são anônimas; se preferir que todos aceitem de novo, é só subir a versão (ADR-0052).
- **Limites dos números:** vêm do aplicativo e **não passam por verificação independente de fraude** (alguém com o app modificado poderia inflar contagens; os limites por IP e por lote só dificultam). É adequado para anunciantes locais; para contratos grandes, seria preciso um serviço de verificação. Um banner sem link não tem cliques.
- Quem tiver o link privado de um banner vê só ele; quem tiver a senha do painel vê tudo. Guarde a senha como a chave do Render.
- **Só as versões novas do app contam.** Aparelhos com uma versão antiga do app não enviam eventos (nem a exibição nem o toque).
- Play Store: o app continua "contendo anúncios", e o formulário de segurança dos dados deve declarar as contagens anônimas de uso.

## Como ligar

1. Rodar `V8__criar_metrica_anuncio.sql` na Neon.
2. No Render, criar a variável `PAINEL_SENHA` (`openssl rand -base64 24`).
3. Publicar o servidor e o app novo (versão 1.1.0 ou mais nova).
4. Abrir `https://<servidor>/painel`, entrar com qualquer usuário e a senha, e copiar o link de cada empresa.
