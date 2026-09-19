# ADR-0011 — Geração de id baseada no maior id já persistido no arquivo

## Status

Substituído pelo [ADR-0046](ADR0046-persistencia-postgresql-e-aceite-de-vinculo.md) (ids gerados pelo banco) e pelo [ADR-0053](ADR0053-remocao-do-terminal-e-do-csv-gui-como-cliente-da-api.md) (o CSV foi removido)

## Contexto

- **Qual problema precisa ser resolvido?** A ADR-0007 definiu ids incrementais gerados em memória (`GerarIdEmMemoriaAdapter`), mas com a persistência em CSV (ADR-0006/0009) implementada, o contador em memória reiniciava do zero a cada execução do programa, gerando ids repetidos que já existiam no arquivo.
- **Por que esse problema é importante?** Ids duplicados quebram a integridade dos dados persistidos — dois registros diferentes com o mesmo id tornam impossível saber a qual objeto uma referência (como o vínculo idoso-familiar ou o histórico) realmente aponta.
- **Limitações e requisitos envolvidos:** o próximo id gerado precisa continuar de onde os dados já persistidos pararam, mesmo depois de reiniciar o programa.
- **Situação atual a modificar:** `GerarIdEmMemoriaAdapter` começava sempre do zero, sem nenhuma consulta ao estado persistido.

## Decisão

Criar o `GerarIdPorArquivoAdapter`, que, na sua criação, lê o arquivo CSV correspondente, encontra o maior id já usado (primeira coluna de cada linha) e inicia o contador a partir dele mais um.

- **O que será utilizado ou alterado?** Nova classe `GerarIdPorArquivoAdapter`, substituindo o `GerarIdEmMemoriaAdapter` nas montagens do `AppConfig`.
- **Como a solução será aplicada?** Cada instância recebe o caminho do arquivo relevante (`usuarios.csv`, `medicamentos.csv`, `historico.csv`) e calcula o próximo id lendo esse arquivo uma única vez, na inicialização.
- **Por que essa alternativa foi escolhida?** Resolve o problema de continuidade entre execuções sem exigir um banco de dados com auto-incremento nativo, mantendo a coerência com a decisão de usar CSV (ADR-0006).

## Alternativas consideradas

### Alternativa 1 — Calcular o próximo id lendo o maior id do arquivo na inicialização

**Vantagens:**
- Ids continuam de forma consistente entre execuções, sem duplicar o que já foi persistido.
- Não exige nenhum arquivo ou estrutura extra além do que já existe.

**Desvantagens:**
- Depende de reler o arquivo inteiro uma vez na inicialização de cada adapter.

### Alternativa 2 — Guardar o último id usado em um arquivo de controle separado

Um arquivo pequeno (ex: `contador_usuarios.txt`) guardando só o último id gerado.

**Vantagens:**
- Não precisa ler o arquivo de dados inteiro para calcular o próximo id.

**Desvantagens:**
- Cria mais um arquivo para manter sincronizado; se ficar dessincronizado do arquivo de dados real (por edição manual, por exemplo), gera inconsistência.

### Alternativa 3 — Trocar para UUID, eliminando a necessidade de calcular sequência

**Vantagens:**
- Elimina de vez o problema de continuidade entre execuções, já que UUID não depende de nenhum estado anterior.

**Desvantagens:**
- Ids longos, difíceis de ler ou comparar manualmente durante o desenvolvimento — já descartado antes na ADR-0007 pelo mesmo motivo.

## Justificativa

- **Manutenibilidade:** resolve o problema de continuidade sem introduzir um novo arquivo ou dependência.
- **Compatibilidade com o projeto:** mantém ids curtos e legíveis, coerente com a decisão da ADR-0007.
- **Custo:** o custo de ler o arquivo uma vez na inicialização é desprezível para o volume de dados atual.

## Consequências

**Positivas**
- Ids não se repetem mais entre execuções diferentes do programa.
- Cada tipo de entidade (usuário, medicamento, histórico) mantém sua própria sequência, lida do respectivo arquivo.

**Negativas**
- Se o arquivo estiver corrompido (linha com formato inválido na primeira coluna), o cálculo do próximo id falha — a robustez dessa leitura depende da integridade do arquivo.
- Ainda não há proteção contra concorrência: dois processos rodando ao mesmo tempo poderiam calcular o mesmo "próximo id" antes de qualquer um salvar.

## Impactos

Afeta `adapter/out/id/` (nova classe `GerarIdPorArquivoAdapter`) e `config/AppConfig`, que passa a instanciar esse adapter em vez do `GerarIdEmMemoriaAdapter` para usuário, medicamento e histórico.

## Implementação

1. Criar `GerarIdPorArquivoAdapter`, recebendo o caminho do arquivo no construtor.
2. Implementar o cálculo do próximo id lendo a primeira coluna de cada linha do arquivo.
3. Substituir as instâncias de `GerarIdEmMemoriaAdapter` no `AppConfig` pelas novas, apontando para `usuarios.csv`, `medicamentos.csv` e `historico.csv`.
4. Testar cadastrando dados, reiniciando o programa, e confirmando que o próximo id gerado continua a sequência correta.

## Observações

Essa decisão assume que o arquivo está bem formado. Um arquivo corrompido (como o incidente da linha grudada em `medicamentos.csv`, resolvido separadamente) pode quebrar esse cálculo — reforça a importância da correção feita na escrita (garantir quebra de linha antes de cada `append`).

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro