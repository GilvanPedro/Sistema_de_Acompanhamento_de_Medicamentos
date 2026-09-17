# ADR-0043 — Entrada do dia da semana em português no cadastro de medicamento

## Status

Aceito

## Contexto

O cadastro e a edição de medicamento, na aplicação de terminal (`PainelMedicamentos`), pediam o dia da semana e convertiam a resposta diretamente com `DayOfWeek.valueOf(texto.toUpperCase())`. Isso exigia que o usuário digitasse o nome do dia em inglês e exatamente como a enum Java espera (`MONDAY`, `TUESDAY`, `WEDNESDAY`, etc.).

Isso trazia dois problemas:
- Pouca usabilidade: o sistema é todo em português, mas essa única entrada exigia inglês, o que não é intuitivo pra quem está usando a aplicação (inclusive o público-alvo do sistema — idosos e familiares).
- Falha silenciosa/abrupta: `DayOfWeek.valueOf(...)` lança `IllegalArgumentException` para qualquer texto que não bata exatamente com a enum, e essa chamada acontecia **fora** do bloco `try/catch` de tratamento de erro em `cadastrar()` e **antes** dele em `editar()`, então uma entrada inválida derrubava o fluxo em vez de mostrar uma mensagem de erro amigável.

Era necessário aceitar o dia da semana em português, em mais de uma forma de escrita comum (com e sem a palavra "feira", com e sem hífen), para todos os dias da semana, tanto no cadastro quanto na edição de medicamento.

## Decisão

Foi criada a classe utilitária `ConverterDiaSemanaUtil`, em `br.com.domain.util`, com um método estático `converter(String textoDigitado)` que:

1. Normaliza o texto: remove espaços nas pontas, deixa em minúsculas e remove acentos (via `Normalizer`).
2. Substitui hífens por espaço e colapsa espaços repetidos, unificando `"sexta-feira"` e `"sexta feira"` na mesma forma.
3. Remove o sufixo `" feira"`, se presente, chegando à forma-base do dia (`"sexta"`, `"segunda"`, `"terca"`, etc.).
4. Consulta um mapa fixo de forma-base → `DayOfWeek` (`segunda`→`MONDAY`, `terca`→`TUESDAY`, `quarta`→`WEDNESDAY`, `quinta`→`THURSDAY`, `sexta`→`FRIDAY`, `sabado`→`SATURDAY`, `domingo`→`SUNDAY`).
5. Se o texto for nulo/vazio ou não corresponder a nenhum dia, lança `DadosInvalidosException` com uma mensagem explicando os formatos aceitos.

Esse método aceita as 3 formas pedidas — `"sexta"`, `"sexta feira"` e `"sexta-feira"` — para todos os 7 dias da semana, incluindo maiúsculas/minúsculas e com/sem acento (`"terça"`, `"TERCA-FEIRA"`, `"Terca Feira"` etc. todos resolvem para `TUESDAY`).

`PainelMedicamentos` passou a chamar `ConverterDiaSemanaUtil.converter(...)` em vez de `DayOfWeek.valueOf(...)` nos dois pontos de entrada (`cadastrar()` e `editar()`), e os prompts do terminal foram atualizados para indicar os formatos aceitos em português. Aproveitando a mudança, a leitura de todos os campos do formulário (nome, horário, dia, tipo) foi movida para dentro do bloco `try/catch` existente, garantindo que uma entrada inválida em qualquer campo — inclusive o dia da semana — seja tratada com uma mensagem de erro em vez de derrubar a aplicação.

## Alternativas consideradas

### Alternativa 1 — Utilitário de conversão com normalização de texto (escolhida)

Criar uma classe utilitária que normaliza a entrada (acentos, hífen, sufixo "feira") e mapeia para `DayOfWeek`.

**Vantagens:**
- Isola a conversão numa única classe, reaproveitável em qualquer ponto de entrada futuro (ex.: se a interface web/gráfica for adicionada depois).
- `Medicamento`, os services e `ValidarDadosMedicamento` continuam trabalhando só com `DayOfWeek`, sem qualquer acoplamento a texto — a tradução fica isolada na camada de entrada, coerente com a arquitetura hexagonal do projeto.
- Cobre as 3 formas pedidas com uma única normalização, sem precisar listar manualmente todas as variações por dia.

**Desvantagens:**
- Mapa fixo de dias em português: se o projeto precisar suportar outro idioma no futuro, essa lógica precisaria ser generalizada (ex.: `Locale`).

### Alternativa 2 — Usar java.time.format.TextStyle com Locale("pt", "BR")

Usar `DayOfWeek.valueOf(...)` combinado com formatação/parsing nativo do `java.time` usando `Locale` português, aproveitando os nomes que a própria JVM já conhece.

**Vantagens:**
- Não exige manter um mapa de tradução manual.
- Usa API padrão do Java para internacionalização.

**Desvantagens:**
- O parsing nativo (`DateTimeFormatter` com `TextStyle.FULL`/`SHORT` e `Locale` pt-BR) é rígido quanto à forma exata do texto (ex.: espera `"segunda-feira"` completo, com acentuação correta, dependendo da implementação de `Locale` da JVM/SO) — não cobre nativamente as 3 variações pedidas (com/sem "feira", com/sem hífen) sem lógica adicional por cima.
- Comportamento pode variar entre JVMs/sistemas operacionais diferentes, já que depende dos dados de localização instalados, tornando o resultado menos previsível que um mapa fixo controlado pelo próprio projeto.

### Alternativa 3 — Validar contra uma lista de Strings aceitas por dia (sem normalização)

Manter uma lista explícita de todas as variações aceitas por dia (ex.: um `Set<String>` com `"sexta"`, `"sexta feira"`, `"sexta-feira"` para cada um dos 7 dias).

**Vantagens:**
- Comportamento totalmente explícito e fácil de auditar linha por linha.

**Desvantagens:**
- Repetição: 7 dias × 3 formas = 21 entradas mantidas manualmente, propensas a divergência (esquecer uma variação, erro de digitação).
- Não lida com variações de maiúsculas/minúsculas ou acentuação sem também duplicar entradas ou normalizar em algum ponto — na prática, acabaria reimplementando parte da normalização da Alternativa 1, só que de forma mais verbosa.

## Justificativa

A Alternativa 1 foi escolhida por resolver o problema com uma normalização simples e genérica, que cobre as 3 formas pedidas para todos os dias sem depender de comportamento variável de `Locale`/JVM (Alternativa 2) nem de uma lista longa e repetitiva de variações (Alternativa 3).

Fatores considerados:
- **Facilidade de desenvolvimento e manutenibilidade**: um único método de normalização cobre todos os casos pedidos, com um mapa pequeno e claro de 7 entradas (forma-base → `DayOfWeek`).
- **Acoplamento**: a tradução fica isolada na camada de adapter de entrada (`PainelMedicamentos` → `ConverterDiaSemanaUtil`), sem vazar para o domínio ou os services, que continuam recebendo `DayOfWeek` já convertido.
- **Compatibilidade com o projeto**: segue o padrão de classes utilitárias já existente em `br.com.domain.util` (`ArquivoCsvUtil`, `LeituraCsvUtil`) e reaproveita `DadosInvalidosException` para sinalizar entrada inválida, consistente com o restante das validações do sistema.

## Consequências

**Positivas**
- O cadastro e a edição de medicamento agora aceitam o dia da semana totalmente em português, em 3 formas de escrita comuns, para qualquer um dos 7 dias.
- Uma entrada de dia da semana inválida agora gera uma mensagem de erro amigável (`DadosInvalidosException`) em vez de derrubar a aplicação de terminal.
- A correção também cobriu a edição de medicamento, que tinha o mesmo problema de exceção fora do `try/catch`.

**Negativas**
- A saída (exibição do dia da semana em `Medicamento.toString()` / na tela de edição) continua em inglês (`FRIDAY`, `MONDAY`, etc.), já que a mudança cobriu apenas a entrada. Isso gera uma pequena inconsistência entre o que o usuário digita e o que é exibido de volta.
- O mapa de tradução é específico do português; suportar múltiplos idiomas no futuro exigiria generalizar essa classe.

## Impactos

- `PainelMedicamentos` (cadastro e edição de medicamento no terminal)
- Novo arquivo `ConverterDiaSemanaUtil` em `br.com.domain.util`
- Nenhum impacto em `Medicamento`, `RegistrarMedicamentoService`, `EditarMedicamentoService`, `ValidarDadosMedicamento` ou nos arquivos CSV, já que todos continuam trabalhando com `DayOfWeek` — a conversão de texto acontece só na borda da aplicação.

## Implementação

1. Criar `ConverterDiaSemanaUtil` em `br.com.domain.util`, com o mapa de dias em português e a normalização de acento/hífen/sufixo "feira".
2. Substituir `DayOfWeek.valueOf(...)` por `ConverterDiaSemanaUtil.converter(...)` em `PainelMedicamentos.cadastrar()` e `PainelMedicamentos.editar()`.
3. Atualizar os textos exibidos no terminal para indicar os formatos aceitos (ex.: "Dia da semana (ex: sexta, sexta feira ou sexta-feira): ").
4. Mover a leitura dos campos do formulário para dentro do bloco `try/catch` já existente em ambos os métodos, garantindo que erros de conversão (dia da semana, tipo de medicamento) sejam tratados com mensagem amigável.
5. Testar manualmente as 3 formas, com e sem acento/maiúsculas, para os 7 dias da semana, no cadastro e na edição.

## Observações

A exibição do dia da semana (saída) continua em inglês. Se for necessário exibi-la também em português (ex.: "Sexta-feira" em vez de "FRIDAY"), isso deve ser tratado numa ADR futura, criando o caminho inverso (`DayOfWeek` → texto em português) para uso na exibição/formatação, sem confundir com a conversão de entrada feita aqui.

## Data

17/09/2026

## Responsáveis

- Gilvan Pedro