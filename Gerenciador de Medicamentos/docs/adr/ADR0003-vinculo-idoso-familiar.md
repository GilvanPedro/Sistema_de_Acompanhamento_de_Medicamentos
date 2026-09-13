# ADR-0003 — Vínculo muitos-para-muitos entre Idoso e Familiar

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Definir como representar o relacionamento entre idosos e familiares dentro do sistema.
- **Por que esse problema é importante?** Esse vínculo é usado diretamente para decidir quem recebe cada notificação — um erro na modelagem afeta a lógica central do sistema.
- **Limitações e requisitos envolvidos:** na vida real, um idoso pode ter mais de um familiar responsável, e um familiar pode acompanhar mais de um idoso (por exemplo, cuidando dos dois pais ao mesmo tempo).
- **Situação atual a modificar:** não havia relacionamento definido antes desta decisão.

## Decisão

Modelar o relacionamento como muitos-para-muitos, com o `Idoso` mantendo uma lista de `Familiar` vinculados, usada tanto para exibir os responsáveis quanto para disparar notificações a todos eles.

- **O que será utilizado ou alterado?** Uma coleção (`List<Familiar>`) dentro da classe `Idoso`.
- **Como a solução será aplicada?** Ao notificar um evento (remédio tomado ou esquecido), o sistema percorre a lista de familiares do idoso e envia o aviso a cada um.
- **Por que essa alternativa foi escolhida?** É a que representa fielmente o cenário de uso real, sem restringir arbitrariamente quantos familiares um idoso pode ter.

## Alternativas consideradas

### Alternativa 1 — Muitos-para-muitos (Idoso mantém lista de Familiar)

Cada idoso guarda a lista de familiares vinculados a ele.

**Vantagens:**
- Representa fielmente o cenário de múltiplos cuidadores.
- Lógica de notificação simples: percorrer a lista do idoso.

**Desvantagens:**
- Exige atenção ao manter a lista sincronizada quando um vínculo for desfeito.
- Ao evoluir para persistência, exige uma tabela de associação (muitos-para-muitos) no banco.

### Alternativa 2 — Um-para-muitos (um familiar por idoso)

Cada idoso teria exatamente um familiar responsável.

**Vantagens:**
- Modelagem mais simples, sem listas.
- Menos código de iteração na notificação.

**Desvantagens:**
- Não reflete a realidade — muitos idosos têm mais de um cuidador.
- Limitaria o sistema de forma artificial desde o início.

### Alternativa 3 — Relação inversa (Familiar mantém lista de Idoso)

Em vez do idoso conhecer seus familiares, o familiar manteria a lista de idosos que acompanha.

**Vantagens:**
- Faz sentido do ponto de vista de "o familiar é quem usa o app para acompanhar".
- Facilita consultas do tipo "quais idosos esse familiar acompanha".

**Desvantagens:**
- Notificar todos os familiares de um idoso (o caso mais comum do sistema) fica mais indireto, exigindo buscar em todos os familiares quem tem aquele idoso na lista.
- Menos natural para o fluxo principal do CuidaMed, que parte do idoso e do medicamento.

## Justificativa

- **Compatibilidade com o projeto:** o fluxo principal do CuidaMed (medicamento → idoso → avisar familiares) parte do idoso, então manter a lista de familiares nele é o caminho mais direto.
- **Facilidade de desenvolvimento:** simplifica a lógica de notificação já implementada em `notificarTomouRemedio` e `notificarEsqueceuRemedio`.
- **Manutenibilidade:** evita lógica de busca reversa (procurar idosos dentro de cada familiar) que seria necessária na Alternativa 3.
- **Escalabilidade:** ao migrar para persistência, a relação muitos-para-muitos é suportada de forma padrão por qualquer banco relacional, via tabela de associação.

## Consequências

**Positivas**
- Notificação a múltiplos familiares já funciona sem lógica condicional extra.
- Modelagem fiel ao cenário real de cuidado compartilhado.

**Negativas**
- Manter a lista de familiares sincronizada exige cuidado ao desfazer vínculos.
- Ao entrar a persistência (ADR-0005), será necessária uma tabela de associação entre idosos e familiares.

## Impactos

Afeta as classes `Idoso` e `Familiar` em `domain/model`, e a lógica de notificação em `NotificarPort` / `ConsoleNotificacaoAdapter`.

## Implementação

1. Adicionar a lista de `Familiar` como atributo de `Idoso`.
2. Implementar métodos de vínculo (adicionar/remover familiar) na classe `Idoso`.
3. Ajustar os métodos de notificação para percorrer essa lista ao avisar sobre um evento.
4. Validar o vínculo no cenário de teste do `Main`, com um idoso associado a mais de um familiar.

## Observações

Quando a persistência for implementada (ver ADR-0005), essa relação exigirá uma tabela de associação (idoso_familiar) caso o banco escolhido seja relacional.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro