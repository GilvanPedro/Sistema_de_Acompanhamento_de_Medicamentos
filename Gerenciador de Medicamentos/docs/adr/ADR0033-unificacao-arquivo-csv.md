# ADR-0033 — Unificação de leitura e escrita de CSV em `ArquivoCsvUtil`

## Status

Substituído pelo [ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md) (`ArquivoCsvUtil` foi removido)

## Contexto

- **Qual problema precisa ser resolvido?** `UsuarioCsvAdapter`, `MedicamentoCsvAdapter` e `HistoricoCsvAdapter` tinham, cada um, sua própria cópia dos métodos `lerLinhas`, `escreverLinha` e uma variação de "reescrever tudo" — código idêntico repetido três vezes, mudando só o nome do arquivo.
- **Por que esse problema é importante?** Além da duplicação em si, a correção da quebra de linha antes de cada `append` (discutida após o incidente da linha grudada em `medicamentos.csv`) nunca havia sido de fato aplicada ao código — continuava existindo o risco de duas escritas se colarem numa única linha corrompida, em qualquer um dos três adapters.
- **Limitações e requisitos envolvidos:** os três adapters precisam continuar funcionando exatamente como antes (mesmo formato de arquivo, mesmo comportamento observável) — a mudança é de organização interna, não de contrato externo.
- **Situação atual a modificar:** os métodos `lerLinhas`, `escreverLinha` e a lógica de reescrita completa do arquivo estavam duplicados, sem a proteção contra linha grudada.

## Decisão

Extrair `lerLinhas`, `escreverLinha` e `reescreverLinhas` para uma classe utilitária compartilhada, `ArquivoCsvUtil`, em `domain/util/`, com métodos estáticos. Embutir nela a checagem de quebra de linha antes de cada `append`, resolvendo ao mesmo tempo a duplicação e o risco de corrupção ainda pendente.

- **O que será utilizado ou alterado?** Nova classe `ArquivoCsvUtil`; os três adapters de persistência passam a delegar leitura, escrita e reescrita a ela, removendo seus métodos privados equivalentes.
- **Como a solução será aplicada?** Cada adapter mantém só o que é específico dele (`montarLinha`, conversão de linha para objeto); tudo que é genérico de "mexer com arquivo de texto" fica centralizado.
- **Por que essa alternativa foi escolhida?** Elimina a triplicação de código sem exigir nenhuma mudança de comportamento observável, e corrige de quebra um risco real que ainda estava pendente desde o incidente com `medicamentos.csv`.

## Alternativas consideradas

### Alternativa 1 — Classe utilitária estática compartilhada (`ArquivoCsvUtil`)

**Vantagens:**
- Elimina a duplicação de três cópias para uma única implementação.
- Corrige, num único lugar, a proteção contra linha grudada — qualquer adapter que usar a classe ganha a proteção automaticamente.

**Desvantagens:**
- Os adapters ficam dependentes de uma classe compartilhada — uma mudança de comportamento nela afeta os três de uma vez (o que também é uma vantagem, dependendo do ângulo).

### Alternativa 2 — Manter a duplicação como está (situação anterior)

**Vantagens:**
- Nenhuma mudança necessária.

**Desvantagens:**
- Perpetua a duplicação e o risco de corrupção ainda não corrigido de fato no código.
- Qualquer correção futura (como essa da quebra de linha) precisaria ser replicada manualmente nos três lugares, com risco de esquecer um deles — que foi exatamente o que aconteceu aqui.

### Alternativa 3 — Classe base abstrata, com os adapters estendendo um `CsvAdapterBase`

Em vez de uma classe utilitária estática, criar uma superclasse com os métodos comuns, e cada adapter estende ela.

**Vantagens:**
- Reaproveitamento de código por herança, um padrão comum em Java.

**Desvantagens:**
- Introduz acoplamento por herança entre adapters que, por natureza, deveriam ser independentes entre si (cada um lida com um arquivo e um tipo de dado diferente) — herança aqui seria usada só pelo reaproveitamento de código, não por uma relação "é um" genuína, o que é considerado um uso indevido de herança.
- Métodos estáticos numa classe utilitária expressam melhor "isso não é sobre o que um adapter é, é sobre uma operação que qualquer um pode usar".

## Justificativa

- **Manutenibilidade:** qualquer ajuste futuro em como o projeto lê ou escreve CSV (por exemplo, trocar o separador, mudar o encoding) passa a ser feito num único lugar.
- **Segurança/Robustez:** a proteção contra linha grudada, que só existia em teoria até agora, passa a valer de fato para os três arquivos.
- **Compatibilidade com o projeto:** segue o mesmo espírito do `LeituraCsvUtil`, já existente, que centraliza a busca com parada antecipada — `ArquivoCsvUtil` centraliza o restante das operações de arquivo que ainda estavam espalhadas.

## Consequências

**Positivas**
- Os três adapters ficaram menores, com só o que é específico de cada um (montagem de linha, conversão de objeto).
- A proteção contra corrupção por linha grudada (incidente já registrado em conversas anteriores) passa a valer de verdade, não só em intenção.

**Negativas**
- `ArquivoCsvUtil` e `LeituraCsvUtil` agora coexistem como duas classes utilitárias de CSV com estilos ligeiramente diferentes (uma com métodos estáticos chamados diretamente pela classe, outra com método estático chamado através de uma instância) — vale unificar o estilo entre as duas no futuro, por consistência.

## Impactos

Afeta `domain/util/ArquivoCsvUtil` (nova classe) e os três adapters de persistência (`UsuarioCsvAdapter`, `MedicamentoCsvAdapter`, `HistoricoCsvAdapter`), que perdem seus métodos privados de leitura/escrita/reescrita em favor de chamadas à nova classe.

## Implementação

1. Criar `ArquivoCsvUtil` com `lerLinhas`, `escreverLinha` (já com a proteção de quebra de linha) e `reescreverLinhas`.
2. Substituir as chamadas equivalentes nos três adapters pelas da nova classe.
3. Remover os métodos privados `lerLinhas`/`escreverLinha`/`reescreverArquivo` (ou equivalentes) de cada adapter.
4. Testar cada operação de cada adapter (salvar, listar, atualizar, excluir, buscar) para confirmar que o comportamento observável não mudou.

## Observações

Vale, no futuro, unificar o estilo de `ArquivoCsvUtil` (métodos estáticos, chamados pela classe) com o de `LeituraCsvUtil` (também estático, mas chamado por instância) — não é urgente, mas é uma inconsistência de estilo que vale corrigir quando outra mudança tocar essas classes.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro