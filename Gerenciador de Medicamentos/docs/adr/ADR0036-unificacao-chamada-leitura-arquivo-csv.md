# ADR-0036 — Unificação do estilo de chamada entre `LeituraCsvUtil` e `ArquivoCsvUtil`

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** `LeituraCsvUtil.buscarPrimeiro` é declarado `static`, mas era chamado através de uma instância (`leituraCsvUtil.buscarPrimeiro(...)`, com um campo `new LeituraCsvUtil()` guardado em cada adapter). `ArquivoCsvUtil` (ADR-0033), criada depois, já nasceu chamada diretamente pela classe (`ArquivoCsvUtil.lerLinhas(...)`), sem instanciar nada.
- **Por que esse problema é importante?** Duas classes utilitárias de CSV convivendo com convenções de chamada diferentes é uma inconsistência de estilo — quem lê o código precisa lembrar qual das duas se chama por instância e qual se chama pela classe, sem nenhuma razão funcional para essa diferença.
- **Limitações e requisitos envolvidos:** chamar um método `static` através de uma referência de instância é permitido pelo Java, mas é redundante — o método não depende de nenhum estado de instância, então instanciar a classe só para chamá-lo não cumpre propósito nenhum.
- **Situação atual a modificar:** o campo `private static LeituraCsvUtil leituraCsvUtil = new LeituraCsvUtil();` em `UsuarioCsvAdapter` e `MedicamentoCsvAdapter`, e as chamadas `leituraCsvUtil.buscarPrimeiro(...)` correspondentes.

## Decisão

Padronizar as duas classes utilitárias (`LeituraCsvUtil` e `ArquivoCsvUtil`) para serem sempre chamadas diretamente pelo nome da classe, sem instanciação. Remover o campo de instância de `LeituraCsvUtil` dos adapters e trocar as chamadas para `LeituraCsvUtil.buscarPrimeiro(...)`.

- **O que será utilizado ou alterado?** `UsuarioCsvAdapter` e `MedicamentoCsvAdapter`, removendo o campo `leituraCsvUtil` e ajustando as chamadas dentro de `buscarPorId`. `LeituraCsvUtil` em si não muda — o método já era `static` desde sua criação.
- **Como a solução será aplicada?** Substituição direta de `leituraCsvUtil.buscarPrimeiro(...)` por `LeituraCsvUtil.buscarPrimeiro(...)`, removendo o campo que guardava a instância.
- **Por que essa alternativa foi escolhida?** É a convenção que já estava correta desde o início em `ArquivoCsvUtil` — alinhar `LeituraCsvUtil` a ela, em vez do caminho inverso, evita alterar o comportamento de duas classes quando corrigir uma já resolve a inconsistência.

## Alternativas consideradas

### Alternativa 1 — Chamar as duas classes utilitárias diretamente pelo nome da classe (sem instanciar)

**Vantagens:**
- Remove a redundância de instanciar uma classe só para chamar um método que não depende de estado nenhum.
- Uniformiza a convenção entre as duas classes utilitárias existentes no projeto.

**Desvantagens:**
- Nenhuma desvantagem relevante — é puramente uma correção de estilo, sem efeito no comportamento do sistema.

### Alternativa 2 — Manter `LeituraCsvUtil` como está, e mudar `ArquivoCsvUtil` para também ser instanciada

**Vantagens:**
- Também unificaria o estilo entre as duas.

**Desvantagens:**
- Introduziria instanciação desnecessária numa classe (`ArquivoCsvUtil`) que já estava correta, só para bater com o padrão da que estava errada — piora em vez de melhorar.

### Alternativa 3 — Transformar as duas em classes não estáticas de verdade, injetadas via construtor como as demais portas/adapters

Tratar `LeituraCsvUtil` e `ArquivoCsvUtil` como dependências injetáveis, ao invés de utilitários estáticos.

**Vantagens:**
- Facilitaria testes que quisessem substituir o comportamento de leitura/escrita por um mock, se isso um dia for necessário.

**Desvantagens:**
- Adiciona complexidade de injeção de dependência para classes que são funções puras sobre arquivos, sem estado próprio — não há benefício real nesse estágio do projeto, e contraria a natureza utilitária que motivou colocá-las fora do padrão de portas/adapters desde o início.

## Justificativa

- **Consistência:** duas classes com o mesmo propósito (utilidades de CSV) passam a seguir exatamente a mesma convenção de uso.
- **Simplicidade:** remove instanciação e um campo por adapter sem nenhum ganho funcional associado a eles.
- **Manutenibilidade:** reduz a chance de alguém copiar o padrão errado (instanciar) ao criar um novo adapter no futuro, já que agora só existe um padrão a seguir.

## Consequências

**Positivas**
- `UsuarioCsvAdapter` e `MedicamentoCsvAdapter` perdem um campo cada, sem perda de funcionalidade.
- As duas classes utilitárias de CSV do projeto agora têm uma única convenção de uso.

**Negativas**
- Nenhuma identificada — mudança puramente de estilo, sem efeito no comportamento observável do sistema.

## Impactos

Afeta `adapter/out/persistence/UsuarioCsvAdapter` e `adapter/out/persistence/MedicamentoCsvAdapter` (remoção do campo `leituraCsvUtil` e ajuste da chamada em `buscarPorId`). `LeituraCsvUtil` não sofre nenhuma alteração interna.

## Implementação

1. Remover o campo `private static LeituraCsvUtil leituraCsvUtil = new LeituraCsvUtil();` de `UsuarioCsvAdapter` e `MedicamentoCsvAdapter`.
2. Trocar `leituraCsvUtil.buscarPrimeiro(...)` por `LeituraCsvUtil.buscarPrimeiro(...)` em ambos.
3. Confirmar que `buscarPorId` continua funcionando exatamente igual nos dois adapters (mesmo resultado, mesma exceção quando não encontra).

## Observações

Esta ADR fecha a inconsistência de estilo já sinalizada nas Observações da ADR-0033, no momento em que `ArquivoCsvUtil` foi criada.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro