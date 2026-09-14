# ADR-0023 — Criação e uso de DadosInvalidosException nas validações

## Status

Aceito

## Contexto

As classes de validação (`ValidarDadosUsuario` e `ValidarDadosMedicamento`) sinalizavam dados inválidos lançando `IllegalArgumentException`, uma exceção genérica do Java.

Isso trazia algumas limitações:
- `IllegalArgumentException` também é usada (ou pode ser usada) para erros de programação, como argumentos incorretos passados por engano entre métodos internos, misturando esse significado com o de "dado de entrada inválido vindo do usuário".
- Não havia como distinguir, em um `catch` nas camadas superiores (adapters, controllers), entre um erro de validação de negócio e um erro de uso indevido da API interna.
- O projeto já possui um pacote `br.com.domain.exception` para exceções de domínio (como `UsuarioNaoEncontradoException`), então faltava uma exceção equivalente para representar dados inválidos.

Era necessário ter uma exceção própria do domínio para representar especificamente falhas de validação de dados de entrada.

## Decisão

Foi criada a classe `DadosInvalidosException`, em `br.com.domain.exception`, estendendo `RuntimeException`:

```java
package br.com.domain.exception;

public class DadosInvalidosException extends RuntimeException {
    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
```

As classes `ValidarDadosUsuario` e `ValidarDadosMedicamento` foram alteradas para lançar `DadosInvalidosException` em vez de `IllegalArgumentException` em todas as verificações de obrigatoriedade e formato.

Por ser uma `RuntimeException`, ela continua unchecked: não exige `throws` na assinatura dos métodos nem `try/catch` obrigatório nos services que chamam as validações.

## Alternativas consideradas

### Alternativa 1 — Exceção própria de domínio, unchecked (escolhida)

Criar `DadosInvalidosException` estendendo `RuntimeException` e usá-la nas classes de validação.

**Vantagens:**
- Semântica clara: a exceção representa exclusivamente "dado de entrada inválido".
- Segue o mesmo padrão já usado no projeto (`UsuarioNaoEncontradoException`), mantendo consistência.
- Não exige alterações nas assinaturas dos métodos existentes nem nos services que já chamam as validações.

**Desvantagens:**
- Assim como `IllegalArgumentException`, por ser unchecked, nada obriga quem chama a tratá-la — depende de documentação/convenção.

### Alternativa 2 — Continuar usando IllegalArgumentException

Manter a exceção genérica do Java, sem criar uma exceção própria.

**Vantagens:**
- Nenhuma mudança necessária, menor esforço.
- Amplamente reconhecida por qualquer desenvolvedor Java.

**Desvantagens:**
- Mistura erros de validação de negócio com erros de uso incorreto de API/argumentos internos.
- Dificulta tratamento diferenciado nas camadas superiores (ex.: mapear para um código HTTP 400 especificamente para dados inválidos).

### Alternativa 3 — Exceção checked (extends Exception)

Criar `DadosInvalidosException` como checked exception.

**Vantagens:**
- Força explicitamente quem chama a tratar o erro de validação.

**Desvantagens:**
- Exigiria `throws` em toda a cadeia de chamadas (services, casos de uso, adapters), aumentando o acoplamento e o "boilerplate".
- Foge do padrão já adotado no projeto, onde as demais exceções de domínio são unchecked.

## Justificativa

A Alternativa 1 foi escolhida por manter consistência com o padrão de exceções já existente no domínio (`UsuarioNaoEncontradoException`) e por resolver o problema real — diferenciar erro de validação de negócio de erro de programação — sem aumentar a complexidade das assinaturas dos métodos.

Fatores considerados:
- **Manutenibilidade**: erros de validação agora têm um tipo único e identificável em todo o sistema.
- **Compatibilidade com o projeto**: segue o padrão já estabelecido de exceções de domínio unchecked.
- **Complexidade**: não exige alterar assinaturas de métodos (`throws`) nem os services que já chamam os validadores.
- **Facilidade de desenvolvimento**: qualquer camada superior pode capturar `DadosInvalidosException` especificamente, sem precisar filtrar `IllegalArgumentException` por mensagem ou contexto.

## Consequências

**Positivas**
- Fica explícito, pelo tipo da exceção, que o erro é de dado inválido vindo da entrada do usuário.
- Camadas superiores (ex.: um futuro `@ControllerAdvice` ou handler de erros) podem tratar `DadosInvalidosException` de forma específica, sem risco de capturar acidentalmente outros `IllegalArgumentException` não relacionados à validação.
- Mantém o padrão de exceções de domínio já usado no projeto.

**Negativas**
- Como é unchecked, ainda depende de disciplina/documentação para ser tratada corretamente nas camadas de entrada.
- Testes que verificavam `IllegalArgumentException` precisam ser atualizados para `DadosInvalidosException`.

## Impactos

- `ValidarDadosUsuario`
- `ValidarDadosMedicamento`
- Testes unitários de `RegistrarUsuarioService`, `EditarUsuarioService`, `RegistrarMedicamentoService` e `EditarMedicamentoService` que verificam o tipo de exceção lançada para dados inválidos.
- Qualquer camada de apresentação/adapter que venha a capturar exceções de validação para mapear respostas de erro.

## Implementação

1. Criar a classe `DadosInvalidosException` em `br.com.domain.exception`, estendendo `RuntimeException`.
2. Substituir `IllegalArgumentException` por `DadosInvalidosException` em `ValidarDadosUsuario`.
3. Substituir `IllegalArgumentException` por `DadosInvalidosException` em `ValidarDadosMedicamento`.
4. Atualizar os testes unitários existentes que verificavam `IllegalArgumentException` para passar a verificar `DadosInvalidosException`.
5. Avaliar, em uma ADR futura, se um handler global de exceções deve mapear `DadosInvalidosException` para uma resposta específica (ex.: HTTP 400).

## Observações

Esta decisão está diretamente relacionada à ADR-0022 (uso das classes de validação nos services): aqui apenas se troca o tipo de exceção lançado pelas validações já integradas naquela ADR.

## Data

14/09/2026

## Responsáveis

- Gilvan Pedro