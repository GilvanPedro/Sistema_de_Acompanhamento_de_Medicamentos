# ADR-0002 — Modelagem de usuários com herança (Usuario, Idoso, Familiar)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** O sistema precisa representar dois papéis de pessoa distintos: o idoso, que toma o medicamento, e o familiar, que acompanha e recebe avisos.
- **Por que esse problema é importante?** Sem uma modelagem clara, esses dois papéis tendem a duplicar atributos e comportamentos comuns (nome, identificação), ou a ficar misturados numa única classe genérica.
- **Limitações e requisitos envolvidos:** os dois papéis compartilham dados básicos, mas têm comportamentos específicos — o idoso possui medicamentos e recebe lembretes, o familiar acompanha idosos e recebe avisos sobre eles.
- **Situação atual a modificar:** não havia modelagem anterior; essa é a decisão fundacional de como representar pessoas no sistema.

## Decisão

Criar uma classe base `Usuario` com os atributos e métodos comuns, e duas subclasses, `Idoso` e `Familiar`, estendendo essa base com o que é específico de cada papel.

- **O que será utilizado ou alterado?** Herança de classes em Java (`extends`).
- **Como a solução será aplicada?** `Idoso` e `Familiar` herdam de `Usuario` e adicionam seus próprios atributos e comportamentos (lista de medicamentos no idoso, lista de idosos acompanhados no familiar).
- **Por que essa alternativa foi escolhida?** Reflete o domínio real de forma direta e evita duplicar código entre os dois papéis.

## Alternativas consideradas

### Alternativa 1 — Herança (Usuario como classe base)

`Usuario` concentra o que é comum; `Idoso` e `Familiar` estendem essa base.

**Vantagens:**
- Evita duplicação de atributos e métodos comuns.
- Reflete o domínio de forma direta e legível.

**Desvantagens:**
- Cria acoplamento mais forte entre as classes do que composição teria.
- Um terceiro papel com comportamento muito diferente exigiria repensar a hierarquia.

### Alternativa 2 — Composição com interface de papel

Uma única classe `Usuario`, com um atributo indicando o papel (idoso ou familiar) e comportamentos delegados a objetos separados.

**Vantagens:**
- Mais flexível para adicionar novos papéis sem herança rígida.
- Evita hierarquias profundas de classes.

**Desvantagens:**
- Mais indireto de ler e entender num projeto pequeno.
- Exige mais código de "colagem" entre a classe e o papel.

### Alternativa 3 — Classes independentes sem relação (Idoso e Familiar sem base comum)

`Idoso` e `Familiar` como classes totalmente separadas, sem nenhuma superclasse.

**Vantagens:**
- Nenhum acoplamento entre as duas classes.
- Simples de implementar isoladamente.

**Desvantagens:**
- Duplicação de atributos e métodos comuns (nome, identificação).
- Qualquer mudança em algo comum (ex: adicionar e-mail) precisa ser feita duas vezes.

## Justificativa

- **Manutenibilidade:** atributos comuns centralizados em um único lugar (`Usuario`).
- **Complexidade:** herança é o modelo mais direto pra esse cenário, sem exigir estruturas extras.
- **Acoplamento:** aceitável, já que idoso e familiar são conceitualmente tipos de usuário, não papéis dinâmicos que mudam em tempo de execução.
- **Facilidade de desenvolvimento:** reduz código repetido logo no início do projeto.
- **Compatibilidade com o projeto:** o domínio do CuidaMed não prevê, por ora, um usuário mudar de papel ou acumular os dois papéis ao mesmo tempo, o que tornaria composição desnecessária.

## Consequências

**Positivas**
- Sem duplicação de atributos comuns entre `Idoso` e `Familiar`.
- Código mais legível, refletindo o domínio real.

**Negativas**
- Acoplamento por herança entre as três classes.
- Possível necessidade de revisão caso surja um papel muito diferente dos dois atuais.

## Impactos

Afeta diretamente as classes `Usuario`, `Idoso` e `Familiar` em `domain/model`, e qualquer código que dependa dessas classes (notificações, histórico de medicamentos).

## Implementação

1. Criar a classe `Usuario` com os atributos e métodos comuns.
2. Criar `Idoso` estendendo `Usuario`, com a lista de medicamentos e de familiares vinculados.
3. Criar `Familiar` estendendo `Usuario`, com a lista de idosos acompanhados.
4. Validar o cenário de teste no `Main` com instâncias de ambos os papéis.

## Observações

Essa decisão foi tomada na fase inicial do projeto, antes da adoção formal da Arquitetura Hexagonal (ADR-0001), mas continua válida dentro da nova estrutura, residindo em `domain/model`.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro