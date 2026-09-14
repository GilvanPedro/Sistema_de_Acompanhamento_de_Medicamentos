# ADR-0024 — Otimização da Leitura de Arquivos CSV com Leitura sob Demanda e Parada Antecipada (Early Exit)

## Status

Aceito

## Contexto

Atualmente, nas operações de busca por ID, edição e exclusão nos adapters de persistência CSV (`MedicamentoCsvAdapter`, `UsuarioCsvAdapter` e `HistoricoCsvAdapter`), a aplicação carrega todo o conteúdo dos arquivos CSV para a memória chamando o método `listarTodos()`.

Qual problema precisa ser resolvido?
- As operações individuais de busca por ID leem o arquivo inteiro até o final e convertem todas as linhas em objetos Java, mesmo que o registro buscado seja a primeira linha do arquivo.
- Alto consumo de memória e processamento desnecessário à medida que o tamanho dos arquivos CSV aumenta.

Por que esse problema é importante?
- Garantir a eficiência do sistema e evitar problemas de estouro de memória (Out Of Memory) ou degradação de desempenho conforme a base de dados cresce.

Quais são as limitações ou requisitos envolvidos?
- O armazenamento utiliza arquivos de texto simples (CSV).
- Os arquivos CSV possuem registros com tamanho variável em bytes, o que impede acesso aleatório por posição/offset direto no disco sem a criação de um índice estruturado.

## Decisão

Foi decidida a criação e utilização da classe utilitária `LeituraCsvUtils` utilizando `BufferedReader` para realizar a leitura de arquivos CSV linha por linha (*stream* sob demanda) com mecanismo de parada antecipada (*early exit*).

A solução será aplicada da seguinte forma:
- A classe utilitária encapsulará o loop de leitura com `BufferedReader`.
- O método de busca lerá o arquivo sequencialmente e interromperá a execução imediatamente no momento em que a condição (`Predicate`) for satisfeita.
- A conversão de texto para objeto de domínio só ocorrerá para a linha encontrada.

## Alternativas consideradas

### Alternativa 1 — Leitura sob Demanda com Parada Antecipada (Early Exit) via `BufferedReader`

Processar o arquivo CSV linha a linha em modo *stream*, encerrando o loop imediatamente ao encontrar o registro pesquisado.

**Vantagens:**
- Consumo de memória constante $O(1)$, pois apenas uma linha é mantida na memória por vez.
- Interrupção imediata da I/O assim que o registro é localizado.
- Implementação simples e de baixo impacto no código existente.

**Desvantagens:**
- Em cenários do pior caso (registro no final ou inexistente), a complexidade de tempo de execução permanece $O(N)$.

### Alternativa 2 — Leitura Integral do Arquivo em Memória + Busca Binária

Carregar todas as linhas para uma lista em memória, ordenar por ID e aplicar o algoritmo de Busca Binária ($O(\log N)$).

**Vantagens:**
- Complexidade da busca em memória $O(\log N)$.

**Desvantagens:**
- Mantém o gargalo principal: obriga a leitura e alocação de todo o arquivo CSV na memória ($O(N)$ em espaço e tempo de leitura de disco) antes de executar a busca.
- Overhead de conversão de todos os registros para objetos Java antes de buscar apenas um.

### Alternativa 3 — Arquivo de Índice Secundário + `RandomAccessFile` (Busca Binária em Disco)

Manter um arquivo de índice de tamanho fixo (`.idx`) associando IDs a deslocamentos em bytes (*offsets*) no CSV e utilizar `RandomAccessFile` para busca binária direta no disco.

**Vantagens:**
- Busca binária real em disco $O(\log N)$ sem carregar o CSV.

**Desvantagens:**
- Complexidade elevada de implementação e manutenção.
- Necessidade de sincronizar o arquivo de índice com o CSV a cada inserção, exclusão ou atualização.

## Justificativa

A **Alternativa 1** foi escolhida por balancear eficiência de memória, simplicidade e manutenibilidade. Embora a busca por ID em arquivos CSV não indexados seja de complexidade $O(N)$, a leitura sob demanda com *early exit* garante que, em média, o sistema leia apenas metade do arquivo (e pare imediatamente quando achar na primeira parte), mantendo a pegada de memória em $O(1)$ sem adicionar a complexidade de gestão de arquivos de índice adicionais.

## Consequências

**Positivas**
- Redução drástica do consumo de memória RAM nas buscas, edições e exclusões.
- Ganho de desempenho em buscas cujos registros estejam localizados nas primeiras linhas do arquivo.
- Isolamento da lógica de leitura de arquivos e concorrência na classe utilitária `LeituraCsvUtils`.

**Negativas**
- Em pesquisas por IDs inexistentes ou posicionados na última linha, todo o arquivo continuará sendo lido linha por linha.

## Impactos

- **Adapters de Persistência:** `MedicamentoCsvAdapter`, `UsuarioCsvAdapter` e `HistoricoCsvAdapter` tiveram seus métodos de busca atualizados para utilizar `LeituraCsvUtils`.
- **Camada Util:** Adição da classe `LeituraCsvUtils` no pacote de utilitários de domínio/infraestrutura.

## Implementação

1. Criar a classe utilitária `LeituraCsvUtils` com o método de busca genérico baseado em `BufferedReader`, `Predicate` e `Function` (mapper).
2. Refatorar o método `buscarPorId` em `MedicamentoCsvAdapter`.
3. Refatorar o método `buscarPorId` em `UsuarioCsvAdapter`.
4. Executar os testes unitários e de integração para validar se os registros continuam sendo recuperados e parseados corretamente.

## Observações

Nenhuma observação adicional.

## Data

14/09/2026

## Responsáveis

- Equipe de Desenvolvimento