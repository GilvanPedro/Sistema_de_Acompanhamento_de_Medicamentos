# ADR-0027 — Exclusão trabalhando com linhas brutas em vez de objetos completos

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `UsuarioCsvAdapter.excluir` e `MedicamentoCsvAdapter.excluir` reconstruíam todos os objetos do arquivo via `listarTodos()` (parseando datas, enums, montando objetos completos) só para filtrar um id e reescrever tudo de volta como string — trabalho desperdiçado para as linhas que permaneceriam exatamente iguais.
- **Por que esse problema é importante?** Excluir um registro não precisa saber o significado de cada campo das outras linhas — só precisa saber qual delas remover. Reconstruir objetos completos para isso é processamento desnecessário. O `HistoricoCsvAdapter.excluir` já seguia o padrão mais enxuto (comparando o id direto na string), evidenciando a inconsistência entre os três adapters.
- **Limitações e requisitos envolvidos:** é fisicamente necessário reescrever o arquivo inteiro para excluir uma linha de um CSV — não há operação de "excluir linha X" nativa. O que pode ser evitado é o custo de parsing das linhas que não serão alteradas, não a leitura do arquivo em si.
- **Situação atual a modificar:** `excluir` em `UsuarioCsvAdapter` e `MedicamentoCsvAdapter` usava `listarTodos()` (reconstrução completa de objetos) e `reescreverArquivo(List<T>)` (reconversão completa para string).

## Decisão

Reescrever `excluir` em `UsuarioCsvAdapter` e `MedicamentoCsvAdapter` para operar diretamente sobre as linhas de texto — lendo o arquivo, comparando o id (primeiro campo, via `split`) sem montar o objeto completo, e reescrevendo as linhas mantidas como estão, sem reconversão. Extrair um método privado `reescreverLinhasBrutas(String arquivo, List<String> linhas)`, reaproveitado também por `removerVinculosDoUsuario`.

- **O que será utilizado ou alterado?** Método `excluir` dos dois adapters, seguindo o padrão já usado em `HistoricoCsvAdapter.excluir`; novo método privado compartilhado `reescreverLinhasBrutas`.
- **Como a solução será aplicada?** Cada linha é comparada pelo id (primeiro campo) sem desserializar o restante dos campos; linhas que não correspondem ao id excluído são mantidas como string, sem qualquer conversão.
- **Por que essa alternativa foi escolhida?** Elimina o trabalho de parsing e reconversão para as linhas que permanecerão inalteradas, sem alterar o comportamento observável do método — o resultado final no arquivo é idêntico ao da versão anterior.

## Alternativas consideradas

### Alternativa 1 — Comparar id direto na string, reescrever linhas mantidas sem reconversão

**Vantagens:**
- Elimina o custo de parsing de campos (data, enum) para linhas que não serão alteradas.
- Unifica o padrão de exclusão entre os três adapters (usuário, medicamento e histórico já seguia esse caminho).

**Desvantagens:**
- Ainda exige ler o arquivo inteiro — inerente ao formato CSV, não eliminável por esta ou nenhuma outra alternativa.

### Alternativa 2 — Manter `listarTodos()` + reconstrução completa (situação anterior)

**Vantagens:**
- Nenhuma mudança de código necessária.

**Desvantagens:**
- Desperdiça processamento reconstruindo e reconvertendo objetos que não sofrerão nenhuma alteração.
- Inconsistente com o padrão já usado no `HistoricoCsvAdapter`.

### Alternativa 3 — Índice de posição de linha por id, permitindo acesso direto sem ler o arquivo inteiro

Manter um índice auxiliar (id → número da linha ou offset no arquivo) para localizar e remover a linha sem varrer tudo.

**Vantagens:**
- Poderia, em teoria, evitar a leitura completa do arquivo para localizar a linha a remover.

**Desvantagens:**
- Mesmo com esse índice, ainda seria necessário reescrever o restante do arquivo por completo (deslocamento de conteúdo em arquivo de texto não é uma operação parcial) — o ganho seria menor do que parece, com complexidade bem maior.
- Exige manter esse índice sincronizado a cada escrita, criando mais uma fonte de inconsistência possível — mesmo raciocínio já descartado na ADR-0010 e ADR-0026.

## Justificativa

- **Desempenho:** reduz o processamento por linha ao mínimo necessário (comparar um campo), sem eliminar a leitura completa do arquivo, que é inerente ao formato.
- **Consistência:** unifica os três adapters de persistência sob o mesmo padrão de exclusão, já validado em produção pelo `HistoricoCsvAdapter`.
- **Manutenibilidade:** o método `reescreverLinhasBrutas` compartilhado reduz duplicação entre `excluir` e `removerVinculosDoUsuario`, que já seguiam essa mesma lógica de forma separada.
- **Compatibilidade com o projeto:** não altera o comportamento observável (o conteúdo final do arquivo é idêntico) — é uma otimização interna, sem risco para quem consome a porta.

## Consequências

**Positivas**
- `excluir` de usuário e medicamento deixa de gastar tempo parseando e reconvertendo dados que não mudam.
- Os três adapters de persistência agora seguem exatamente o mesmo padrão de exclusão.
- Menos duplicação de código entre `excluir` e `removerVinculosDoUsuario`.

**Negativas**
- `reescreverArquivo(List<T>)` (que reconstrói a partir de objetos completos) continua existindo, usado apenas por `atualizar` — o adapter passa a ter dois caminhos de reescrita (um para objeto completo, outro para linha bruta), o que exige que quem mantém o código entenda quando cada um se aplica.

## Impactos

Afeta `adapter/out/persistence/UsuarioCsvAdapter` (método `excluir` reescrito, novo `reescreverLinhasBrutas`, `removerVinculosDoUsuario` ajustado para reaproveitar o novo método) e `adapter/out/persistence/MedicamentoCsvAdapter` (método `excluir` reescrito, `reescreverLinhasBrutas` próprio).

## Implementação

1. Extrair `reescreverLinhasBrutas(String arquivo, List<String> linhas)` em cada adapter que precisar (ou considerar movê-lo para uma classe utilitária compartilhada, como `LeituraCsvUtil`, no futuro).
2. Reescrever `excluir` em `MedicamentoCsvAdapter`, comparando o id direto na string.
3. Reescrever `excluir` em `UsuarioCsvAdapter`, comparando o id direto na string, mantendo a chamada a `removerVinculosDoUsuario` ao final.
4. Ajustar `removerVinculosDoUsuario` para reaproveitar `reescreverLinhasBrutas` em vez de seu próprio bloco de escrita.
5. Testar excluindo um usuário e um medicamento, confirmando que o conteúdo final do arquivo é idêntico ao produzido pela versão anterior.

## Observações

O método `reescreverLinhasBrutas` foi duplicado em cada adapter que precisa dele. Se essa duplicação incomodar conforme o projeto crescer, é candidata a migrar para `LeituraCsvUtil` (ADR anterior, criada para centralizar utilidades de leitura de CSV), junto com a leitura de linhas (`lerLinhas`), que também se repete nos três adapters.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro