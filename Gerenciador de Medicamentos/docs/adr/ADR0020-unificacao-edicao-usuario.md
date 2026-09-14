# ADR-0020 — Unificação da edição de usuário em um único método (`editarUsuario`)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** A primeira versão do `EditarUsuarioService` tinha dois métodos separados, `editarIdoso` e `editarFamiliar`, cada um fazendo cast do `Usuario` buscado para o subtipo esperado antes de editar.
- **Por que esse problema é importante?** Os três campos editáveis (`nome`, `email`, `senha`) pertencem todos à classe base `Usuario`, não a `Idoso` ou `Familiar` especificamente. O cast exigia que quem chamasse o serviço já soubesse de antemão qual era o tipo do usuário, e um erro nessa escolha gerava `ClassCastException` — o que de fato aconteceu ao chamar `editarIdoso` com o id de um `Familiar`.
- **Limitações e requisitos envolvidos:** diferente do cadastro (`registrarIdoso`/`registrarFamiliar`), que precisa saber o tipo para instanciar a subclasse correta, a edição opera sobre um objeto que já existe e já é do tipo que é — não há necessidade de informar o tipo de novo.
- **Situação atual a modificar:** `editarIdoso(int id, ...)` e `editarFamiliar(int id, ...)`, cada um com cast e checagem de tipo repetida, apesar de fazerem exatamente a mesma coisa internamente.

## Decisão

Unificar os dois métodos em um único `editarUsuario(int id, String nome, String email, String senha)`, operando diretamente sobre `Usuario`, sem nenhum cast para subtipo.

- **O que será utilizado ou alterado?** `EditarUsuarioCase` passa a ter um único método; `EditarUsuarioService` remove o cast e a checagem `instanceof` que existiam nas versões separadas.
- **Como a solução será aplicada?** O serviço busca o `Usuario` pelo id, altera os três campos comuns via seus setters (já disponíveis na classe base) e persiste com `atualizar`.
- **Por que essa alternativa foi escolhida?** Como nenhum dos campos editados é específico de `Idoso` ou `Familiar`, manter dois métodos e um cast só introduzia uma fonte de erro (o tipo errado) para um problema que não existia de verdade.

## Alternativas consideradas

### Alternativa 1 — Método único `editarUsuario`, operando sobre `Usuario`

**Vantagens:**
- Elimina o cast e o risco de `ClassCastException`.
- Uma única implementação para manter, já que a lógica de edição é idêntica para os dois subtipos.

**Desvantagens:**
- Se no futuro um campo específico de `Idoso` ou `Familiar` precisar ser editável, esse método único não vai cobrir esse caso — vai exigir um método adicional só para aquele campo.

### Alternativa 2 — Manter dois métodos separados, mas validar o tipo antes do cast

Continuar com `editarIdoso`/`editarFamiliar`, adicionando um `instanceof` antes do cast, lançando `IllegalArgumentException` se o tipo não bater.

**Vantagens:**
- Evita o `ClassCastException`, sem precisar redesenhar a interface.

**Desvantagens:**
- Mantém uma exigência de tipo que não tem motivo de existir — quem chama precisaria saber de antemão o tipo do usuário só para escolher qual dos dois métodos chamar, uma informação desnecessária para editar campos comuns.

### Alternativa 3 — Método único, mas devolvendo `Object` para o chamador decidir o cast

**Vantagens:**
- Nenhuma vantagem real identificada frente às outras opções.

**Desvantagens:**
- Perde completamente a tipagem, transferindo o problema do cast para fora do serviço, sem resolvê-lo — só descartado por completude de análise.

## Justificativa

- **Manutenibilidade:** um único método para entender e manter, em vez de dois quase idênticos.
- **Facilidade de desenvolvimento:** quem chama o serviço não precisa mais saber, de antemão, se o id é de um idoso ou de um familiar — só precisa saber o id.
- **Segurança/Robustez:** elimina de vez a classe de erro que gerou o `ClassCastException`, ao invés de apenas mitigá-la com uma checagem adicional (Alternativa 2).
- **Compatibilidade com o projeto:** reflete corretamente a estrutura de herança já usada em `Usuario`/`Idoso`/`Familiar` (ADR-0002) — os campos comuns pertencem à base, e é nela que a edição deveria operar.

## Consequências

**Positivas**
- `EditarUsuarioService` fica mais simples, sem cast nem `instanceof`.
- Impossível editar o usuário errado por confundir `editarIdoso` com `editarFamiliar` — só existe um método agora.

**Negativas**
- Caso surja, no futuro, um campo editável específico de `Idoso` ou `Familiar` (por exemplo, algo relacionado à lista de vínculos), esse método único não cobre esse caso — vai exigir um método adicional, específico, quando essa necessidade aparecer.

## Impactos

Afeta `domain/port/in/EditarUsuarioCase` (assinatura simplificada) e `application/service/EditarUsuarioService` (implementação simplificada).

## Implementação

1. Reduzir `EditarUsuarioCase` a um único método `editarUsuario(int id, String nome, String email, String senha)`.
2. Reescrever `EditarUsuarioService`, removendo o cast e a checagem de tipo, operando diretamente sobre `Usuario`.
3. Ajustar qualquer chamada existente (`Main`, `Teste2.java`) para usar o novo método único.

## Observações

Essa unificação vale como critério geral para decisões futuras: campos que pertencem à classe base (`Usuario`) devem ser editados através de um método que opera sobre a base, não sobre os subtipos — o cast só deve entrar quando o campo editado for, de fato, específico de um subtipo.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro