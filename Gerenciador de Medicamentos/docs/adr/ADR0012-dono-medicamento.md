# ADR-0012 — Medicamento passa a ter um idoso dono (campo `idosoId`)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `Medicamento` não tinha nenhuma referência a qual idoso ele pertencia — o vínculo só existia indiretamente, através do `HistoricoMedicamento`, e só depois que uma tomada já tinha sido registrada.
- **Por que esse problema é importante?** Para verificar se um remédio passou do horário e ainda não foi tomado (necessário para a notificação de atraso), o sistema precisa saber, de antemão, a quem aquele medicamento pertence — sem isso, não há como saber quem notificar.
- **Limitações e requisitos envolvidos:** cada medicamento pertence a exatamente um idoso (diferente do vínculo idoso-familiar, que é muitos-para-muitos), então a solução não precisa de uma tabela de associação à parte.
- **Situação atual a modificar:** `Medicamento` não possuía nenhum campo de referência a `Idoso`.

## Decisão

Adicionar o campo `idosoId` à classe `Medicamento`, referenciando o id do idoso dono daquele medicamento.

- **O que será utilizado ou alterado?** Novo campo `idosoId` no construtor de `Medicamento`; ajuste em cascata no `RegistrarMedicamentoCase`/`RegistrarMedicamentoService` (novo parâmetro) e no `MedicamentoCsvAdapter` (nova coluna no CSV).
- **Como a solução será aplicada?** O id do idoso passa a ser informado no momento do cadastro do medicamento, persistido como uma coluna a mais em `medicamentos.csv`.
- **Por que essa alternativa foi escolhida?** É a forma mais direta de modelar uma relação um-para-muitos (um idoso pode ter vários medicamentos, cada medicamento pertence a um único idoso), sem exigir um arquivo de vínculo separado como o `vinculos.csv` da ADR-0003.

## Alternativas consideradas

### Alternativa 1 — Campo `idosoId` direto em `Medicamento`

**Vantagens:**
- Simples: um campo a mais, sem necessidade de arquivo de associação separado.
- Reflete corretamente a cardinalidade real da relação (um-para-muitos).

**Desvantagens:**
- Exigiu migração dos dados já persistidos em `medicamentos.csv` no formato antigo, sem essa coluna.

### Alternativa 2 — Arquivo de vínculo separado (`medicamentos_idosos.csv`), igual ao padrão usado para idoso-familiar

**Vantagens:**
- Segue o mesmo padrão já estabelecido na ADR-0003.

**Desvantagens:**
- Adiciona complexidade desnecessária para uma relação que é, na prática, um-para-muitos, não muitos-para-muitos.

### Alternativa 3 — Resolver o dono do medicamento só através do histórico (situação anterior)

Continuar sem vínculo direto, inferindo o idoso apenas quando havia um registro de histórico associado.

**Vantagens:**
- Nenhuma mudança de modelo necessária.

**Desvantagens:**
- Impossível saber a quem um medicamento pertence antes de qualquer tomada ser registrada — inviabiliza a verificação de atraso, que precisa saber isso de antemão.

## Justificativa

- **Compatibilidade com o projeto:** viabiliza diretamente a funcionalidade de verificação de atraso, que dependia dessa informação.
- **Manutenibilidade:** modela a relação real (um-para-muitos) da forma mais simples possível.
- **Custo:** o custo foi pontual — migrar os dados já existentes — não recorrente.

## Consequências

**Positivas**
- Cada medicamento agora sabe, desde o cadastro, a quem pertence.
- Viabiliza a verificação de atraso (ADR-0014) e qualquer futura consulta do tipo "quais medicamentos esse idoso toma".

**Negativas**
- Quebrou a compatibilidade com os dados já persistidos no formato antigo de `medicamentos.csv`, exigindo migração manual das linhas existentes.
- Aumentou o número de parâmetros do cadastro de medicamento.

## Impactos

Afeta `domain/model/Medicamento`, `domain/port/in/RegistrarMedicamentoCase`, `application/service/RegistrarMedicamentoService` e `adapter/out/persistence/MedicamentoCsvAdapter`.

## Implementação

1. Adicionar o campo `idosoId` à classe `Medicamento` e ajustar seu construtor.
2. Atualizar a assinatura de `RegistrarMedicamentoCase`/`RegistrarMedicamentoService` para receber o `idosoId`.
3. Atualizar `MedicamentoCsvAdapter` para escrever e ler essa nova coluna.
4. Migrar manualmente as linhas já existentes em `medicamentos.csv` para o novo formato, adicionando a coluna de `idosoId` com base no histórico já registrado.

## Observações

Essa mudança quebrou a compatibilidade com dados já persistidos no formato antigo — um incidente real ocorreu durante a migração manual, causando um `NumberFormatException` até as linhas antigas serem corrigidas. Vale registrar como lição: mudanças de formato de arquivo persistido precisam vir acompanhadas de um script de migração, não só de correção manual pontual.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro