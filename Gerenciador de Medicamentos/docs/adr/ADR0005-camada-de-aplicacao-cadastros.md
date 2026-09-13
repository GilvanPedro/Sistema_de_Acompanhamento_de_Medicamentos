# ADR-0005 — Camada de aplicação para cadastro de usuários e medicamentos, com validações na criação

## Status

Proposto

## Contexto

- **Qual problema precisa ser resolvido?** Hoje, idosos, familiares e medicamentos são montados na mão dentro do cenário de teste do `Main`, sem nenhuma função reutilizável de cadastro e sem nenhuma verificação sobre os dados recebidos.
- **Por que esse problema é importante?** Sem uma camada dedicada de cadastro, qualquer ponto de entrada futuro (console, API REST) precisaria repetir a lógica de montar e validar os objetos, e dados inválidos (nome vazio, horário nulo, medicamento sem tipo) poderiam entrar no sistema sem barreira nenhuma.
- **Limitações e requisitos envolvidos:** o cadastro de usuário precisa cobrir tanto `Idoso` quanto `Familiar`, e o cadastro de medicamento precisa estar vinculado a um idoso já existente. As verificações de criação (campos obrigatórios, valores válidos) precisam acontecer antes do objeto ser considerado válido no sistema.
- **Situação atual a modificar:** não existe hoje nenhuma classe de serviço ou de entrada organizando esse fluxo — tudo é feito diretamente no `Main`, sem separação entre "receber dados" e "validar e criar".

## Decisão

Criar casos de uso de cadastro no domínio (`RegistrarUsuarioUseCase`, `RegistrarMedicamentoUseCase`), implementados por serviços de aplicação (`RegistrarUsuarioService`, `RegistrarMedicamentoService`) que concentram as regras de validação antes de instanciar e devolver os objetos.

- **O que será utilizado ou alterado?** Novas interfaces em `domain/port/in`, novas classes em `application/service`, e adaptação do `Main` (em `adapter/in/console`) para chamar esses serviços em vez de montar os objetos diretamente.
- **Como a solução será aplicada?** O serviço recebe os dados brutos (nome, tipo de usuário, dados do medicamento), aplica as verificações necessárias e só então cria e devolve o objeto de domínio. Se alguma verificação falhar, o serviço rejeita a criação.
- **Por que essa alternativa foi escolhida?** Centraliza a regra de validação num único lugar, reaproveitável por qualquer adapter de entrada (console hoje, API REST amanhã), sem duplicar verificação em cada ponto de entrada.

## Alternativas consideradas

### Alternativa 1 — Validação na camada de aplicação (serviço)

As classes `RegistrarUsuarioService` e `RegistrarMedicamentoService` verificam os dados antes de criar os objetos de domínio.

**Vantagens:**
- Centraliza a regra de validação num único lugar, reaproveitado por qualquer adapter de entrada.
- Mantém os models de domínio simples, sem lógica de verificação misturada com os dados.

**Desvantagens:**
- Exige disciplina para sempre passar pela camada de serviço, nunca instanciar os models diretamente fora dela.

### Alternativa 2 — Validação dentro dos próprios models (construtores)

`Idoso`, `Familiar` e `Medicamento` validam seus próprios dados dentro do construtor, lançando exceção se algo estiver errado.

**Vantagens:**
- Impossível criar um objeto de domínio inválido, em qualquer ponto do código.
- Não depende de lembrar de chamar um serviço específico.

**Desvantagens:**
- Mistura regra de validação de cadastro com a modelagem pura do domínio.
- Regras de validação que dependem de mais de um objeto (ex: medicamento só pode ser criado se o idoso já existir) não cabem bem dentro de um construtor isolado.

### Alternativa 3 — Validação na camada de entrada (adapter/controller)

Cada adapter de entrada (console, controller web) valida os dados recebidos antes de repassar para o domínio.

**Vantagens:**
- Permite mensagens de erro específicas do formato de entrada (ex: erro de formulário web vs. erro de linha de comando).

**Desvantagens:**
- Duplica a regra de validação em cada novo adapter de entrada (console, web, etc.), já que a mesma verificação de negócio precisaria ser repetida.
- Corre o risco de dois adapters diferentes validarem a mesma regra de forma divergente.

## Justificativa

- **Manutenibilidade:** com a validação centralizada no serviço, qualquer ajuste de regra é feito em um único lugar.
- **Acoplamento:** o `adapter/in/console` (e futuramente o `adapter/in/web`) fica simples, só repassando dados para o caso de uso, sem conhecer as regras de validação.
- **Testabilidade:** os serviços de cadastro podem ser testados diretamente, cobrindo casos válidos e inválidos, sem precisar simular console ou web.
- **Facilidade de desenvolvimento:** evita duplicar verificação quando o `adapter/in/web` for implementado no futuro.
- **Compatibilidade com o projeto:** segue o mesmo padrão já usado nas portas de notificação (ADR-0004) — regra de negócio isolada, adapters só conectando entrada e saída.

## Consequências

**Positivas**
- Uma única função de cadastro de usuário e uma de medicamento, reaproveitáveis por qualquer forma de entrada futura.
- Dados inválidos (nome vazio, horário nulo, medicamento sem idoso vinculado) são barrados antes de virarem objetos de domínio.

**Negativas**
- Mais uma camada de código entre "receber dado" e "objeto criado", comparado ao que era feito direto no `Main`.
- É preciso manter a disciplina de sempre cadastrar através do serviço, nunca instanciando os models diretamente em outro lugar do código.

## Impactos

Afeta `domain/port/in` (novas interfaces `RegistrarUsuarioUseCase`, `RegistrarMedicamentoUseCase`), `application/service` (novas classes de serviço), e `adapter/in/console/Main.java`, que passa a chamar esses serviços em vez de montar os objetos na mão.

## Implementação

1. Criar as interfaces `RegistrarUsuarioUseCase` e `RegistrarMedicamentoUseCase` em `domain/port/in`.
2. Implementar `RegistrarUsuarioService`, com verificações como nome obrigatório e tipo de usuário válido (idoso ou familiar).
3. Implementar `RegistrarMedicamentoService`, verificando nome do medicamento, horário, dia da semana, tipo e existência do idoso vinculado.
4. Ajustar o `Main` para chamar esses serviços no cenário de teste, em vez de instanciar `Idoso`, `Familiar` e `Medicamento` diretamente.
5. Testar os casos de erro (nome vazio, medicamento sem idoso, dados nulos) pra garantir que a criação é rejeitada corretamente.

## Observações

Essa ADR substitui a proposta anterior de introdução de persistência via portas de saída. A persistência segue como próximo passo do projeto, mas será registrada em ADR própria quando for retomada — esta decisão cobre apenas a camada de cadastro e validação.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro