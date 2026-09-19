# ADR-0009 — Implementação completa da persistência em CSV (criar, listar, atualizar, excluir)

## Status

Substituído pelo [ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md) (os adaptadores CSV foram removidos)

## Contexto

- **Qual problema precisa ser resolvido?** A ADR-0006 decidiu usar CSV como forma de persistência, mas só na intenção — faltava implementar de fato os adapters, cobrindo não só salvar e listar, mas também editar e excluir dados já persistidos.
- **Por que esse problema é importante?** Sem edição e exclusão, qualquer erro de cadastro (nome errado, medicamento duplicado) ficaria permanente no arquivo, sem forma de corrigir.
- **Limitações e requisitos envolvidos:** arquivo de texto não suporta alterar ou remover uma linha "no meio" — qualquer mudança exige reescrever o arquivo inteiro. O vínculo idoso-familiar (ADR-0003), guardado à parte em `vinculos.csv`, também precisa ser limpo quando um usuário é excluído, para não deixar referência a um id que não existe mais.
- **Situação atual a modificar:** os adapters de persistência (`UsuarioCsvAdapter`, `MedicamentoCsvAdapter`, `HistoricoCsvAdapter`) só sabiam salvar (append) e listar todos.

## Decisão

Implementar `atualizar` e `excluir` nos três adapters de persistência, seguindo o padrão: ler tudo para memória, aplicar a mudança na lista, reescrever o arquivo inteiro (`TRUNCATE_EXISTING`). No `UsuarioCsvAdapter`, a exclusão também remove qualquer linha de `vinculos.csv` que referencie o id excluído (como idoso ou como familiar).

- **O que será utilizado ou alterado?** Métodos `atualizar` e `excluir` adicionados às portas `SalvarUsuarioPort`, `SalvarMedicamentoPort` e `SalvarHistoricoPort`, e implementados nos respectivos adapters.
- **Como a solução será aplicada?** Ler → filtrar/substituir na lista em memória → reescrever o arquivo do zero com `StandardOpenOption.TRUNCATE_EXISTING`.
- **Por que essa alternativa foi escolhida?** É a única forma viável de editar/remover dados num arquivo de texto simples, sem introduzir um banco de dados nesta fase.

## Alternativas consideradas

### Alternativa 1 — Ler tudo, modificar em memória, reescrever o arquivo inteiro

**Vantagens:**
- Simples de implementar, sem dependências externas.
- Reaproveita o mesmo `listarTodos()` já existente como base da leitura.

**Desvantagens:**
- Reescreve o arquivo inteiro mesmo para alterar uma única linha — ineficiente para arquivos grandes.

### Alternativa 2 — Marcar registros como "excluído" sem removê-los (soft delete)

Manter a linha no arquivo, com uma coluna extra indicando se está ativa ou excluída.

**Vantagens:**
- Preserva histórico de dados removidos.
- Evita reescrever o arquivo inteiro a cada exclusão.

**Desvantagens:**
- Complica a leitura, que precisaria sempre filtrar os registros "excluídos".
- Exige alterar o formato de todas as linhas para incluir a nova coluna.

### Alternativa 3 — Migrar para um banco de dados real neste momento

Resolver o problema de edição/exclusão adotando SQLite ou similar, em vez de CSV.

**Vantagens:**
- Suporte nativo a `UPDATE`/`DELETE`, sem reescrever arquivo nenhum.

**Desvantagens:**
- Antecipa uma decisão (ADR-0006 já registrou CSV como solução intermediária) sem necessidade real neste estágio do projeto.

## Justificativa

- **Compatibilidade com o projeto:** mantém a decisão já tomada na ADR-0006, sem introduzir tecnologia nova antes da hora.
- **Manutenibilidade:** o padrão "ler, modificar, reescrever" é repetido nos três adapters, facilitando entender qualquer um deles depois de entender o primeiro.
- **Custo:** nenhuma dependência nova, nenhum esforço de configuração.

## Consequências

**Positivas**
- Cadastros errados podem ser corrigidos ou removidos sem precisar apagar o arquivo inteiro na mão.
- Exclusão de usuário já cuida de limpar vínculos órfãos automaticamente.

**Negativas**
- Reescrever o arquivo inteiro a cada edição/exclusão não escala bem para uma base de dados grande.
- Lógica de "ler tudo, modificar, reescrever" se repete em três classes diferentes, sem estar centralizada.

## Impactos

Afeta `domain/port/out/SalvarUsuarioPort`, `SalvarMedicamentoPort`, `SalvarHistoricoPort` (novos métodos) e seus respectivos adapters em `adapter/out/persistence/`.

## Implementação

1. Adicionar `atualizar` e `excluir` às três portas de saída.
2. Implementar os dois métodos em cada adapter, seguindo o padrão ler/modificar/reescrever.
3. No `UsuarioCsvAdapter`, implementar a limpeza de `vinculos.csv` ao excluir um usuário.
4. Testar cada operação isoladamente antes de integrar aos serviços de aplicação.

## Observações

A repetição do padrão ler/reescrever entre os três adapters é uma duplicação conhecida e aceita por ora — candidata a ser extraída para uma classe utilitária comum quando o projeto crescer (ver observações da ADR-0006).

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro