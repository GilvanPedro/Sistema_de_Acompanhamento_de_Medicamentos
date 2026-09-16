# ADR-0037 — Correção da população de vínculos em UsuarioCsvAdapter.buscarPorId

## Status

Aceito

## Contexto

`UsuarioCsvAdapter` possui dois métodos que retornam usuários individuais ou em lista, com comportamentos diferentes quanto aos vínculos idoso↔familiar:

- `listarTodos()` lê `usuarios.csv`, monta todos os `Idoso`/`Familiar`, depois lê `vinculos.csv` e chama `adicionarFamiliares`/`adicionarIdosos` para cada par encontrado. Os objetos retornados por esse método sempre vêm com os vínculos corretamente populados.
- `buscarPorId(int id)` foi otimizado para usar `LeituraCsvUtil.buscarPrimeiro`, que lê `usuarios.csv` linha a linha e para assim que encontra o id procurado, convertendo a linha direto em `Idoso` ou `Familiar` via `converterLinhaParaUsuario`. Esse caminho **nunca lia `vinculos.csv`**, então o objeto retornado sempre vinha com a lista de familiares (ou idosos) vazia, independentemente dos vínculos realmente existentes.

Esse comportamento inconsistente só se manifestava quando algum fluxo usava `buscarPorId` para obter um `Idoso` e depois dependia da lista de familiares dele — por exemplo, `RegistrarTomadaService.registrarTomada(...)`, que chama `notificarPort.avisarRemedioTomado(idoso, medicamento)` / `avisarRemedioEsquecido(idoso, medicamento)`. Como `ConsoleNotificationAdapter` itera `idoso.getFamiliares()` para decidir a quem notificar, um idoso obtido via `buscarPorId` nunca gerava nenhuma mensagem, mesmo tendo vínculos salvos em `vinculos.csv`. O erro era silencioso: nenhuma exceção era lançada, apenas nenhuma notificação era emitida.

## Decisão

`buscarPorId` passou a, depois de localizar o usuário base pela leitura otimizada em `usuarios.csv`, também consultar `vinculos.csv` e popular a lista de vínculos do usuário encontrado — espelhando o que `listarTodos()` já fazia, mas escopado a um único usuário:

- Se o usuário encontrado for um `Idoso`, o método varre `vinculos.csv` procurando linhas em que `idosoId` bate com o id dele, busca o `Familiar` correspondente (por id, em `usuarios.csv`) e chama `idoso.adicionarFamiliares(familiar)` para cada vínculo encontrado.
- Se for um `Familiar`, o processo é análogo: busca cada `Idoso` vinculado e chama `familiar.adicionarIdosos(idoso)`.

Essa varredura foi extraída para um método privado `popularVinculos(Usuario usuario)`, e a busca do usuário do outro lado do vínculo usa um novo método auxiliar `buscarUsuarioBasico(int id)`, que reaproveita `LeituraCsvUtil.buscarPrimeiro` sem popular vínculos recursivamente (evitando recursão infinita e sendo suficiente, já que só é preciso nome/e-mail do outro lado nesse contexto).

`buscarPorEmail` não precisou de alteração, pois já delega para `listarTodos()`, que já populava os vínculos corretamente.

## Alternativas consideradas

### Alternativa 1 — Popular vínculos dentro de buscarPorId, reaproveitando busca otimizada (escolhida)

Manter a leitura otimizada (`buscarPrimeiro`, que para no primeiro match) para localizar o usuário, e adicionar uma segunda leitura de `vinculos.csv` só para esse usuário.

**Vantagens:**
- Mantém o ganho de performance da busca early-exit para encontrar o usuário em si.
- Corrige o bug sem alterar a assinatura do método nem o contrato da interface `SalvarUsuarioPort`.
- Não duplica a lógica de `listarTodos()`, que carrega todos os usuários na memória mesmo quando só um é necessário.

**Desvantagens:**
- Ainda faz uma leitura completa de `vinculos.csv` (sem early-exit, já que pode haver múltiplos vínculos para o mesmo usuário) e, para cada vínculo encontrado, mais uma leitura em `usuarios.csv` via `buscarUsuarioBasico`. Para um usuário com muitos vínculos, isso gera várias leituras sequenciais do arquivo de usuários.

### Alternativa 2 — Implementar buscarPorId delegando para listarTodos()

Reescrever `buscarPorId` para chamar `listarTodos()` e filtrar pelo id, do mesmo jeito que `buscarPorNome` e `buscarPorEmail` já fazem.

**Vantagens:**
- Reaproveita 100% da lógica de população de vínculos já testada em `listarTodos()`.
- Menos código novo, menor risco de introduzir uma segunda implementação divergente da regra de vínculos.

**Desvantagens:**
- Perde o ganho de performance do early-exit: para buscar um único usuário, carrega e converte todos os usuários e todos os vínculos do sistema inteiro na memória.
- Em bases de dados maiores, isso pode se tornar um gargalo perceptível para uma operação que deveria ser barata (buscar por id).

### Alternativa 3 — Não popular vínculos em buscarPorId; documentar a limitação

Manter `buscarPorId` como estava (sem vínculos) e deixar explícito, via nome do método ou Javadoc, que ele não traz vínculos — quem precisar deles deve usar `listarTodos()`.

**Vantagens:**
- Nenhuma mudança de código além de documentação.
- Mantém o método mais rápido possível.

**Desvantagens:**
- Não resolve o bug relatado: os fluxos existentes (como `RegistrarTomadaService`) já chamam `buscarPorId` esperando um usuário completo, e mudar todos esses pontos de chamada para usar `listarTodos()` só pra ter os vínculos seria mais invasivo e custoso do que corrigir o método em si.
- Mantém uma armadilha na API: dois métodos de "buscar usuário" com comportamento diferente quanto a vínculos, sem nenhuma indicação no tipo de retorno.

## Justificativa

A Alternativa 1 foi escolhida por resolver o bug relatado — notificações de tomada de medicamento não chegando aos familiares — sem abrir mão do ganho de performance que motivou a reescrita de `buscarPorId` para usar `LeituraCsvUtil.buscarPrimeiro`, e sem alterar o contrato da porta `SalvarUsuarioPort`.

Fatores considerados:
- **Corretude**: elimina o comportamento inconsistente entre `buscarPorId` e `listarTodos()`, que é a causa raiz do bug.
- **Desempenho**: mantém a busca do usuário principal com early-exit; o custo adicional (ler vínculos e o usuário do outro lado) só ocorre para o usuário efetivamente buscado, não para a base inteira.
- **Compatibilidade**: não muda a assinatura de `buscarPorId` nem o comportamento de outros métodos já corretos (`listarTodos`, `buscarPorEmail`, `buscarPorNome`).

## Consequências

**Positivas**
- `RegistrarTomadaService.registrarTomada(...)` volta a notificar corretamente os familiares vinculados ao idoso, tanto quando o remédio é tomado quanto quando é esquecido.
- Qualquer outro fluxo que já usava ou venha a usar `buscarPorId` para obter um `Idoso`/`Familiar` com vínculos passa a funcionar corretamente, sem precisar saber dessa particularidade.
- Reduz a divergência de comportamento entre os métodos de busca da classe, facilitando a manutenção futura.

**Negativas**
- `buscarPorId` fica mais caro do que a versão anterior (que era rápida, porém incorreta): agora faz, no pior caso, uma leitura de `vinculos.csv` mais N leituras de `usuarios.csv` (uma por vínculo encontrado). Para usuários com muitos vínculos, pode valer revisitar a estratégia de leitura (por exemplo, indexação em memória ou cache) em uma ADR futura.
- A lógica de "buscar usuário básico sem vínculos" (`buscarUsuarioBasico`) introduz um terceiro comportamento de busca na classe (além de `buscarPorId`, com vínculos, e `listarTodos`), o que exige atenção para não confundir os dois no futuro.

## Impactos

- `UsuarioCsvAdapter.buscarPorId`
- Indiretamente, todos os consumidores desse método que dependem de vínculos populados — hoje, principalmente `RegistrarTomadaService` (via `Teste2`) e qualquer fluxo do `Main` que busque um idoso/familiar específico por id para exibir ou notificar vínculos.
- Nenhuma mudança de schema nos arquivos CSV (`usuarios.csv`, `vinculos.csv`).

## Implementação

1. Extrair a leitura de vínculos para um novo método privado `popularVinculos(Usuario usuario)`.
2. Adicionar o método auxiliar privado `buscarUsuarioBasico(int id)`, reaproveitando `LeituraCsvUtil.buscarPrimeiro` sem popular vínculos.
3. Chamar `popularVinculos(usuario)` em `buscarPorId` antes de retornar o usuário encontrado.
4. Rodar `Teste2` novamente para confirmar que a notificação de tomada/esquecimento de medicamento passa a chegar aos familiares vinculados.
5. Avaliar, em uma ADR futura, se vale a pena unificar a lógica de população de vínculos entre `listarTodos()` e `buscarPorId()` (por exemplo, com um método privado comum) para reduzir duplicação.

## Observações

Esse bug foi identificado a partir de um relato de comportamento ("a mensagem de tomada não está sendo enviada aos familiares"), não de uma exceção — reforça a importância de testes que verifiquem o conteúdo dos vínculos retornados por cada método de busca, não só a ausência de erros.

## Data

16/09/2026

## Responsáveis

- Gilvan Pedro