# ADR-0025 — Exceção dedicada para linhas corrompidas nos arquivos CSV

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Quando uma linha de um arquivo CSV vem malformada (por edição manual, ou por um bug de escrita como o da linha grudada em `medicamentos.csv`), o erro que aparecia era uma exceção técnica crua (`NumberFormatException`, `DateTimeParseException`, `IllegalArgumentException` de enum), sem dizer qual arquivo nem qual linha estava com problema.
- **Por que esse problema é importante?** Um incidente real já aconteceu: uma linha corrompida em `medicamentos.csv` gerou um `NumberFormatException: For input string: "Losartana"`, exigindo caçar manualmente, linha por linha, qual delas estava malformada — sem nenhuma pista direta de onde olhar.
- **Limitações e requisitos envolvidos:** os erros de parsing possíveis variam por tipo de campo (número, horário, dia da semana, enum), cada um lançando um tipo de exceção diferente nativamente. A solução precisa cobrir todos esses casos sem exigir um `catch` para cada tipo específico.
- **Situação atual a modificar:** `UsuarioCsvAdapter`, `MedicamentoCsvAdapter` e `HistoricoCsvAdapter` convertiam texto em dado tipado sem nenhum tratamento de erro, deixando a exceção original subir sem contexto adicional.

## Decisão

Criar `ArquivoCsvCorrompidoException`, em `domain/exception/`, e envolver todo ponto de conversão de texto para tipo (em todos os três adapters de persistência CSV) num `try/catch (RuntimeException e)`, relançando como essa exceção nova, com o nome do arquivo, o conteúdo da linha problemática e a exceção original preservada como causa.

- **O que será utilizado ou alterado?** Nova classe `ArquivoCsvCorrompidoException`; todos os métodos de leitura/parsing de `UsuarioCsvAdapter`, `MedicamentoCsvAdapter` e `HistoricoCsvAdapter` passam a capturar e relançar erros de parsing.
- **Como a solução será aplicada?** Cada bloco de conversão de linha em objeto é envolvido num `try/catch (RuntimeException e) { throw new ArquivoCsvCorrompidoException(arquivo, linha, e); }`.
- **Por que essa alternativa foi escolhida?** Captura qualquer tipo de erro de parsing (número, data, enum) numa única exceção nomeada, sem perder a causa original, e sem precisar de um `catch` específico para cada tipo de campo.

## Alternativas consideradas

### Alternativa 1 — Exceção de domínio própria, envolvendo cada ponto de parsing com `catch (RuntimeException e)`

**Vantagens:**
- Cobre qualquer tipo de erro de parsing (número, data, enum) com um único bloco de captura.
- Preserva a exceção original como causa, sem perder informação de debug.
- Mensagem de erro já indica o arquivo e a linha exata, sem precisar caçar manualmente.

**Desvantagens:**
- Exige adicionar `try/catch` em cada ponto de conversão, em vez de um tratamento central único.

### Alternativa 2 — Deixar a exceção original subir sem tratamento (situação anterior)

**Vantagens:**
- Nenhuma mudança de código necessária.

**Desvantagens:**
- Mensagem de erro não indica qual arquivo nem qual linha causou o problema, como já ocorreu no incidente real com `medicamentos.csv`.

### Alternativa 3 — Validar o formato da linha antes de tentar converter (validação prévia, sem depender de capturar exceção)

Checar o número de campos e o formato de cada um antes de chamar `Integer.parseInt`/`LocalTime.parse`/etc., devolvendo um erro customizado sem depender de exceções nativas.

**Vantagens:**
- Evita depender de capturar exceções do Java para controle de fluxo, que é considerado por alguns um antipadrão.

**Desvantagens:**
- Exigiria reimplementar manualmente a validação de formato que `Integer.parseInt`, `LocalTime.parse` e os `valueOf` de enum já fazem — puro retrabalho para reproduzir uma checagem que a própria linguagem oferece.

## Justificativa

- **Manutenibilidade:** erros de arquivo corrompido agora vêm com contexto suficiente para corrigir sem precisar investigar manualmente linha por linha.
- **Facilidade de desenvolvimento:** um único padrão (`try/catch (RuntimeException e)`) cobre todos os tipos de erro de parsing possíveis nos CSVs do projeto.
- **Compatibilidade com o projeto:** resolve diretamente o tipo de incidente que já ocorreu na prática (linha grudada em `medicamentos.csv`), não é uma prevenção teórica.
- **Testabilidade:** fica mais fácil escrever um teste que confirma que uma linha malformada gera `ArquivoCsvCorrompidoException` com a mensagem esperada, em vez de uma exceção técnica genérica.

## Consequências

**Positivas**
- Qualquer linha corrompida em qualquer um dos quatro arquivos CSV do projeto agora gera um erro que já aponta o arquivo e a linha exata.
- A causa original (`NumberFormatException`, `DateTimeParseException`, etc.) continua acessível via `getCause()`, sem perda de informação de debug.

**Negativas**
- O `try/catch` precisa ser adicionado manualmente em cada novo ponto de parsing que surgir — não há garantia automática de que um adapter futuro vá seguir esse padrão, a menos que quem escrever lembre de aplicá-lo.
- Aumenta ligeiramente o tamanho de cada método de leitura, com o bloco `try/catch` envolvendo a lógica de conversão.

## Impactos

Afeta `domain/exception/ArquivoCsvCorrompidoException` (nova classe), e os métodos de leitura de `UsuarioCsvAdapter` (`listarTodos`, `removerVinculosDoUsuario`), `MedicamentoCsvAdapter` (`listarTodos`) e `HistoricoCsvAdapter` (`montarObjeto`, `excluir`).

## Implementação

1. Criar `ArquivoCsvCorrompidoException` em `domain/exception/`, recebendo o nome do arquivo, a linha e a causa original.
2. Envolver a conversão de campos em `MedicamentoCsvAdapter.listarTodos()` com `try/catch`, relançando a nova exceção.
3. Envolver os dois pontos de conversão em `UsuarioCsvAdapter.listarTodos()` (leitura de usuários e de vínculos) e em `removerVinculosDoUsuario`.
4. Envolver `HistoricoCsvAdapter.montarObjeto()` e `HistoricoCsvAdapter.excluir()`.
5. Testar provocando uma linha malformada em cada um dos quatro arquivos e confirmando que a exceção nova aparece com a mensagem esperada.

## Observações

Esse padrão deve ser aplicado em qualquer adapter CSV novo que vier a existir no projeto — a decisão de "todo ponto de parsing de CSV precisa desse tratamento" vale como convenção geral, não só para os três adapters existentes hoje.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro