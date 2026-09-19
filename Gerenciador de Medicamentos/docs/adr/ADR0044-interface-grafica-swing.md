# ADR-0044 — Interface gráfica em Swing, pensada para idosos

## Status

Aceito. Desde o [ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md), a interface gráfica fala com a API em vez de acessar o banco

## Contexto

Até aqui a única forma de usar o sistema era o terminal (`adapter/in/console`), com menus numerados e digitação de textos como horário (`HH:mm`) e tipo do medicamento. Para o público-alvo (idosos e familiares) isso é difícil: exige digitar formatos exatos, a letra é pequena e não há como ajustá-la.

Requisitos da nova interface:
- Fontes grandes e ajustáveis pelo próprio usuário.
- Poucas opções por tela, com textos diretos e botões grandes.
- Modo claro e modo escuro.
- Reaproveitar toda a regra de negócio existente, sem alterar `domain/` nem `application/`.

## Decisão

Foi criado um novo adaptador de entrada, `br.com.adapter.in.gui`, usando **Swing** (já vem no JDK, sem novas dependências no `pom.xml`). O ponto de entrada é `GuiApp`. O terminal continua funcionando normalmente.

Principais escolhas:
- **Tema (`Tema`)**: duas paletas (clara e escura) com contraste alto, e cinco tamanhos de letra (botões `A−` / `A+` na barra superior). Tema e tamanho ficam salvos com `java.util.prefs.Preferences` e voltam na próxima abertura. Espaçamentos crescem junto com a letra.
- **Componentes próprios** (`Botao`, `Escolha`, `Campo`, `Cartao`, `Texto`, barra de rolagem): desenhados por nós para que cores e fontes acompanhem o tema (o Look and Feel padrão do Swing não escurece de forma confiável), com foco de teclado bem visível.
- **Layouts próprios** (`Pilha`, `GradeAuto`): empilham a tela na vertical e calculam a altura dos textos a partir da largura real, e os botões lado a lado passam para a linha de baixo quando a letra é grande. Nada fica cortado; a tela rola.
- **Entrada sem digitação livre** sempre que possível: tipo do remédio e dia da semana são botões de escolha; hora e minutos usam botões `+` / `−`. Com isso o `ConverterDiaSemanaUtil` (ADR-0043) não é necessário na GUI, pois já se trabalha com `DayOfWeek`.
- **Confirmações grandes** (`Dialogos`) no lugar do `JOptionPane`; o botão padrão de "Excluir" é o "Não, manter".
- **Mensagens de erro em português claro**, no topo da tela (`Rotulos.erro`); erros inesperados não expõem detalhes técnicos.
- Estados nunca dependem só de cor: "Tomou" / "Não tomou" aparecem escritos, além do verde/vermelho.

As telas usam apenas as portas e serviços já expostos por `AppConfig`, como o terminal.

## Alternativas consideradas

### Alternativa 1 — Swing (escolhida)
**Vantagens:** sem dependências novas; roda em qualquer JDK 17; mantém a arquitetura hexagonal (mais um adaptador de entrada).
**Desvantagens:** o visual exige componentes próprios para ficar moderno e acompanhar o tema.

### Alternativa 2 — JavaFX
**Vantagens:** CSS para temas e visual mais moderno.
**Desvantagens:** exige adicionar módulos/plugin do JavaFX ao Maven e configuração de execução mais trabalhosa.

### Alternativa 3 — Web (HTML/CSS + servidor Java)
**Vantagens:** acessível de celular e tablet; já previsto em `adapter/in/web`.
**Desvantagens:** exige servidor HTTP, sessão e mais código; foge do escopo desta etapa.

## Consequências

**Positivas**
- Uso por botões grandes, sem precisar decorar formatos de digitação.
- Letra e tema ajustáveis e lembrados entre execuções.
- Nenhuma alteração em `domain/` e `application/`.

**Negativas**
- Dois adaptadores de entrada (terminal e GUI) para manter em paralelo.
- Não há testes automatizados de tela (o projeto ainda não tem testes); a interface foi verificada renderizando as telas em imagens nos modos claro/escuro e em letra máxima, e exercitando os fluxos de formulário.
- O verificador de atraso periódico (`AgendadorVerificacaoAtraso`) não é iniciado pela GUI, tal como no terminal; os avisos aparecem ao abrir a tela inicial.

## Impactos

- Novo pacote `br.com.adapter.in.gui` (arquivos `Tela*`, `Navegador`, `Tema` e componentes).
- `README.md` atualizado com a forma de executar a interface gráfica.

## Data

18/09/2026

## Responsáveis

- Gilvan Pedro
