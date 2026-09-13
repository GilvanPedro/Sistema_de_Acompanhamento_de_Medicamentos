# ADR-0010 — Busca de usuário por nome

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Não havia nenhuma forma de encontrar um usuário específico sem percorrer manualmente a lista inteira retornada por `listarTodos()`.
- **Por que esse problema é importante?** Operações como vincular um familiar a um idoso, editar ou excluir um cadastro específico exigem localizar a pessoa certa primeiro.
- **Limitações e requisitos envolvidos:** a busca deve funcionar tanto para idosos quanto para familiares, sem exigir o id exato — o caso de uso mais comum é buscar por um nome (ou parte dele) lembrado de cabeça.
- **Situação atual a modificar:** `SalvarUsuarioPort` só oferecia `listarTodos()`, sem nenhum filtro.

## Decisão

Adicionar o método `buscarPorNome(String nome)` à porta `SalvarUsuarioPort`, implementado no `UsuarioCsvAdapter` como uma busca parcial e sem diferenciar maiúsculas de minúsculas sobre a lista completa já carregada.

- **O que será utilizado ou alterado?** Novo método na porta `SalvarUsuarioPort` e sua implementação no adapter.
- **Como a solução será aplicada?** Reaproveita `listarTodos()` internamente, filtrando os resultados cujo nome contém o termo buscado (`toLowerCase().contains(...)`).
- **Por que essa alternativa foi escolhida?** É simples, reaproveita a leitura já existente, e cobre o caso de uso real sem exigir índice ou estrutura de busca mais sofisticada para o volume de dados atual.

## Alternativas consideradas

### Alternativa 1 — Filtro sobre `listarTodos()`, busca parcial case-insensitive

**Vantagens:**
- Simples de implementar, sem necessidade de nome exato.
- Reaproveita a leitura já existente do CSV.

**Desvantagens:**
- Percorre a lista inteira a cada busca — não escala para uma base de dados muito grande.

### Alternativa 2 — Busca exigindo nome exato (case-sensitive)

**Vantagens:**
- Implementação trivial, comparação direta de strings.

**Desvantagens:**
- Pouco tolerante a erro de digitação ou variação de capitalização, tornando a busca frustrante na prática.

### Alternativa 3 — Índice de nomes mantido à parte, atualizado a cada escrita

Manter uma estrutura auxiliar (ex: mapa de nome para id) atualizada a cada cadastro/edição, evitando percorrer tudo a cada busca.

**Vantagens:**
- Busca mais rápida para uma base de dados grande.

**Desvantagens:**
- Complexidade adicional desnecessária para o volume de dados atual do projeto.
- Exige manter esse índice sincronizado a cada escrita, criando mais uma fonte de inconsistência possível.

## Justificativa

- **Facilidade de desenvolvimento:** filtro simples sobre a lista já carregada, sem infraestrutura nova.
- **Compatibilidade com o projeto:** o volume de dados do CuidaMed é pequeno, então o custo de percorrer a lista inteira é irrelevante na prática.
- **Testabilidade:** fácil de testar, passando diferentes variações de capitalização e nomes parciais.

## Consequências

**Positivas**
- Permite localizar um usuário por nome sem saber o id de antemão, viabilizando o fluxo de vincular familiar a idoso pelo nome.
- Busca tolerante a variação de maiúsculas/minúsculas e a nomes parciais.

**Negativas**
- Desempenho degrada linearmente com o tamanho da base de dados — aceitável agora, mas não é uma solução definitiva.

## Impactos

Afeta `domain/port/out/SalvarUsuarioPort` (novo método) e `adapter/out/persistence/UsuarioCsvAdapter` (implementação).

## Implementação

1. Adicionar `buscarPorNome(String nome)` à interface `SalvarUsuarioPort`.
2. Implementar no `UsuarioCsvAdapter`, filtrando o resultado de `listarTodos()`.
3. Usar esse método no `Main` para localizar idosos/familiares antes de vincular ou editar.

## Observações

Se a base de dados crescer significativamente, essa busca deve ser revisada — a Alternativa 3 (índice auxiliar) ou a migração para um banco com suporte a consultas indexadas passam a fazer mais sentido.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro