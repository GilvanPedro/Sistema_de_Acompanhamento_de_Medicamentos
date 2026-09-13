# ADR-0007 — Geração de identificadores (IDs) das entidades

## Status

Proposto

## Contexto

- **Qual problema precisa ser resolvido?** Definir uma forma consistente de gerar identificadores únicos para as entidades do sistema (`Usuario`/`Idoso`/`Familiar`, `Medicamento`, `HistoricoMedicamento`).
- **Por que esse problema é importante?** Hoje, `Usuario` e `HistoricoMedicamento` recebem o `id` como parâmetro do construtor, informado manualmente em cada criação. `Medicamento` sequer possui um campo de identificador. Com a persistência em CSV (ADR-0006) e o cadastro via serviço (ADR-0005) entrando em cena, é preciso um jeito confiável de gerar esses ids sem depender de alguém digitar um número na mão.
- **Limitações e requisitos envolvidos:** os ids precisam ser únicos dentro de cada tipo de entidade, servir de referência nas relações persistidas em CSV (ex: o `HistoricoMedicamento` referenciando `Medicamento` e `Idoso`), e continuar simples de gerar num projeto local, sem banco de dados com auto-incremento nativo.
- **Situação atual a modificar:** o id de `Usuario` e `HistoricoMedicamento` é definido manualmente em cada chamada de construtor (visível no cenário de teste do `Main`), o que é propenso a erro — nada impede dois objetos criados com o mesmo id.

## Decisão

Adotar geração automática de ids incrementais (inteiros sequenciais), calculados pelo adapter de persistência com base no maior id já existente, e adicionar um campo de id também à classe `Medicamento`, que hoje não possui um.

- **O que será utilizado ou alterado?** Os construtores de `Usuario` e `HistoricoMedicamento` deixam de receber o `id` como parâmetro externo; passam a recebê-lo já gerado pelo serviço de cadastro (ADR-0005), que consulta o adapter de persistência (ADR-0006) para saber o próximo id disponível. `Medicamento` ganha um campo `id`, seguindo o mesmo padrão.
- **Como a solução será aplicada?** Ao carregar os dados do CSV, o adapter de persistência calcula o maior id já usado para aquele tipo de entidade e soma 1 para gerar o próximo, repassando esse valor para o serviço no momento da criação.
- **Por que essa alternativa foi escolhida?** É simples de implementar sem banco de dados, gera ids curtos e fáceis de ler dentro dos arquivos CSV, e elimina o risco de duplicidade que existe ao informar o id manualmente.

## Alternativas consideradas

### Alternativa 1 — ID incremental gerado pelo adapter de persistência

O adapter calcula o próximo id disponível com base nos registros já existentes no CSV.

**Vantagens:**
- Ids curtos, legíveis e fáceis de conferir manualmente dentro do arquivo CSV.
- Elimina a necessidade de qualquer pessoa informar o id na hora de criar um objeto.

**Desvantagens:**
- Depende de ler o estado atual dos dados antes de gerar o próximo id, o que pode gerar concorrência se o sistema evoluir para múltiplos processos simultâneos.

### Alternativa 2 — UUID (identificador único universal)

Cada entidade recebe um identificador gerado por algoritmo, sem depender de nenhum contador (ex: `550e8400-e29b-41d4-a716-446655440000`).

**Vantagens:**
- Garantia de unicidade sem precisar consultar dados existentes.
- Funciona bem mesmo em cenários com múltiplos processos gerando ids ao mesmo tempo.

**Desvantagens:**
- Ids longos e difíceis de ler ou digitar manualmente durante testes num projeto pequeno.
- Overhead desnecessário para o volume de dados atual do CuidaMed.

### Alternativa 3 — ID informado manualmente na criação (situação atual)

Continuar exigindo que quem cria o objeto informe o id diretamente, como acontece hoje no `Main`.

**Vantagens:**
- Nenhuma mudança necessária no código existente.

**Desvantagens:**
- Propenso a erro humano — nada impede a criação de dois objetos com o mesmo id.
- Não escala para um cadastro real feito por um serviço (ADR-0005), onde quem cadastra não deveria precisar saber ou escolher um id.

## Justificativa

- **Facilidade de desenvolvimento:** ids incrementais são simples de implementar sem depender de bibliotecas externas.
- **Manutenibilidade:** ids curtos facilitam a leitura e depuração dos arquivos CSV (ADR-0006) durante o desenvolvimento.
- **Compatibilidade com o projeto:** o volume de dados do CuidaMed é pequeno e local, então o principal risco do UUID (unicidade em ambientes distribuídos) não se aplica aqui.
- **Segurança:** ids sequenciais expõem menos preocupação nesse contexto, já que o sistema não é uma API pública exposta na internet nesta fase.
- **Testabilidade:** fácil de prever e verificar em testes, já que o próximo id é determinístico a partir do estado atual dos dados.

## Consequências

**Positivas**
- Elimina o risco de ids duplicados por erro humano.
- `Medicamento` passa a ter identidade própria, necessária para referenciá-lo corretamente no histórico e na persistência em CSV.
- Quem cadastra (via serviço da ADR-0005) não precisa mais se preocupar em escolher um id.

**Negativas**
- A geração de id passa a depender da leitura do estado atual dos dados persistidos, criando uma dependência entre o serviço de cadastro e o adapter de persistência.
- Ids sequenciais podem exigir revisão caso o projeto evolua para múltiplos usuários operando ao mesmo tempo sobre a mesma base de dados.

## Impactos

Afeta `domain/model/Usuario`, `domain/model/Medicamento` (novo campo `id`) e `domain/model/HistoricoMedicamento`, além dos serviços em `application/service` (que passam a obter o id gerado antes de criar o objeto) e dos adapters em `adapter/out/persistence` (responsáveis por calcular o próximo id disponível).

## Implementação

1. Adicionar o campo `id` à classe `Medicamento`, seguindo o mesmo padrão de `Usuario` e `HistoricoMedicamento`.
2. Implementar, em cada adapter de persistência CSV, um método que retorna o próximo id disponível para aquele tipo de entidade.
3. Ajustar `RegistrarUsuarioService` e `RegistrarMedicamentoService` (ADR-0005) para obter o próximo id junto ao adapter antes de criar o objeto de domínio.
4. Remover a passagem manual de id no cenário de teste do `Main`, deixando essa responsabilidade totalmente a cargo do serviço.
5. Testar a geração sequencial ao cadastrar múltiplos usuários e medicamentos em uma mesma execução e entre execuções diferentes (persistindo e recarregando o CSV).

## Observações

Se o projeto evoluir no futuro para múltiplos usuários operando simultaneamente sobre a mesma base de dados, essa decisão deve ser revisitada — nesse cenário, UUID ou um mecanismo de auto-incremento de banco de dados relacional tende a ser mais seguro do que um contador calculado em memória.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro