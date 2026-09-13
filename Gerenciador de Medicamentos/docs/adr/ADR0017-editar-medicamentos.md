# ADR-0017 — Implementação real do serviço de edição de medicamento

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `EditarMedicamentoService` existia como um método vazio, sem receber nenhuma dependência no construtor e sempre devolvendo `null` — uma edição de medicamento nunca funcionava de fato.
- **Por que esse problema é importante?** Sem edição real, qualquer correção num medicamento cadastrado (nome, horário, dia, tipo) exigia excluir e recadastrar, perdendo o id original.
- **Limitações e requisitos envolvidos:** a assinatura original de `editarMedicamento` não recebia o `id` do medicamento a editar, o que tornava impossível saber qual registro alterar. O parâmetro de tipo também estava incorreto, recebendo `String` em vez do enum `TipoMedicamento`.
- **Situação atual a modificar:** `EditarMedicamentoCase`/`EditarMedicamentoService` eram apenas um esqueleto, sem lógica nem injeção de dependência.

## Decisão

Corrigir a assinatura de `editarMedicamento` para receber o `id` do medicamento e o `TipoMedicamento` como enum (em vez de `String`), e implementar o serviço usando `buscarPorId` (ADR-0016) para localizar o medicamento, aplicar as mudanças via setters e persistir com `atualizar`.

- **O que será utilizado ou alterado?** Assinatura de `EditarMedicamentoCase`, construtor e corpo de `EditarMedicamentoService`.
- **Como a solução será aplicada?** Busca o medicamento pelo id; se não encontrar, lança `IllegalArgumentException`; se encontrar, atualiza os campos do objeto em memória e persiste via `SalvarMedicamentoPort.atualizar`.
- **Por que essa alternativa foi escolhida?** É a forma mais direta de implementar edição reaproveitando a infraestrutura de busca e persistência já existente, sem introduzir nada novo além do necessário.

## Alternativas consideradas

### Alternativa 1 — Buscar por id, aplicar mudanças via setters, persistir com `atualizar`

**Vantagens:**
- Reaproveita `buscarPorId` e `atualizar`, ambos já existentes nas portas.
- Simples de entender: busca, modifica, salva.

**Desvantagens:**
- Depende dos setters existirem em `Medicamento` — qualquer campo sem setter não pode ser editado por esse caminho.

### Alternativa 2 — Criar um novo objeto `Medicamento` imutável a cada edição, em vez de usar setters

Em vez de modificar o objeto encontrado, construir um novo `Medicamento` com os dados atualizados e o mesmo id.

**Vantagens:**
- Evita mutação de estado, aproximando o modelo de um estilo mais imutável.

**Desvantagens:**
- `Medicamento` já expõe setters e é tratado como mutável em outras partes do código — mudar esse estilo só na edição criaria inconsistência de abordagem dentro da mesma classe.

### Alternativa 3 — Deixar a validação de "medicamento existe" para quem chama o serviço, não para o serviço em si

O serviço apenas tenta atualizar; quem chama é responsável por checar antes se o id existe.

**Vantagens:**
- Serviço mais simples, sem lançar exceção.

**Desvantagens:**
- Transfere uma responsabilidade que é naturalmente do serviço (garantir que só se edita o que existe) para quem usa a interface, aumentando o risco de um chamador esquecer dessa checagem.

## Justificativa

- **Manutenibilidade:** a lógica de "buscar, validar existência, aplicar mudanças, persistir" fica centralizada no serviço, não espalhada em quem o chama.
- **Compatibilidade com o projeto:** reaproveita a porta `buscarPorId` recém-criada (ADR-0016) e o `atualizar` já existente desde a ADR-0009.
- **Facilidade de desenvolvimento:** corrige, de quebra, um erro de tipo que já existia (`String` em vez do enum `TipoMedicamento`), evitando erros de digitação na hora de editar o tipo.

## Consequências

**Positivas**
- Edição de medicamento passa a funcionar de verdade, sem precisar excluir e recadastrar.
- Erro claro (`IllegalArgumentException`) quando alguém tenta editar um id que não existe, em vez de falha silenciosa.

**Negativas**
- Ainda não há validação de dados na edição (nome vazio, horário nulo) — mesma lacuna já identificada no `RegistrarUsuarioService`/`RegistrarMedicamentoService`.
- O padrão de mutação via setters segue o estilo já usado no projeto, mas significa que o objeto `Medicamento` original é alterado em memória antes mesmo de confirmar que a persistência funcionou.

## Impactos

Afeta `domain/port/in/EditarMedicamentoCase` (assinatura corrigida) e `application/service/EditarMedicamentoService` (implementação completa), além do `AppConfig`, que ganhou `criarEditarMedicamentoService()`.

## Implementação

1. Corrigir a assinatura de `EditarMedicamentoCase`, adicionando `id` e trocando `String tipoMedicamento` por `TipoMedicamento`.
2. Implementar o construtor de `EditarMedicamentoService`, recebendo `SalvarMedicamentoPort`.
3. Implementar `editarMedicamento`: buscar por id, validar existência, aplicar mudanças, persistir.
4. Adicionar `criarEditarMedicamentoService()` ao `AppConfig`.
5. Testar editando um medicamento existente e tentando editar um id inexistente, conferindo os dois comportamentos.

## Observações

Falta ligar a validação de dados (`ValidarDadosMedicamento`, já existente em `domain/validation/`) antes de aplicar as mudanças — mesma pendência já registrada para o cadastro, ainda não resolvida para a edição.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro