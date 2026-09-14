# ADR-0022 — Utilização das classes de validação nos Services

## Status

Aceito

## Contexto

As classes de validação (`ValidarDadosUsuario`, `ValidarDadosMedicamento`, `ValidarEmail` e `ValidarInformacoesVazias`) já existiam no pacote `br.com.domain.validation`, mas não estavam sendo utilizadas pelos services da camada de aplicação (`RegistrarUsuarioService`, `EditarUsuarioService`, `RegistrarMedicamentoService`, `EditarMedicamentoService`).

Isso significava que:
- Dados de usuário (nome, e-mail, senha) e de medicamento (nome, dia da semana, horário, tipo) podiam ser persistidos sem qualquer verificação de obrigatoriedade ou formato.
- As regras de validação já implementadas e testáveis não tinham efeito real no comportamento do sistema.
- Não havia barreira de proteção antes de os dados chegarem às portas de saída (`SalvarUsuarioPort`, `SalvarMedicamentoPort`).

Era necessário conectar essas validações ao fluxo de registro e edição, garantindo que dados inválidos sejam rejeitados antes de qualquer efeito colateral (geração de id, persistência, etc.).

## Decisão

As classes de validação passam a ser instanciadas e chamadas no início de cada método de negócio dos services envolvidos:

- `RegistrarUsuarioService.registrarIdoso` e `registrarFamiliar` chamam `ValidarDadosUsuario.validarRegistro(nome, email, senha)`.
- `EditarUsuarioService.editarUsuario` chama `ValidarDadosUsuario.validarRegistro(nome, email, senha)`.
- `RegistrarMedicamentoService.registrarMedicamento` chama `ValidarDadosMedicamento.validarMedicamento(nome, diaSemana, horario, tipo)`.
- `EditarMedicamentoService.editarMedicamento` chama `ValidarDadosMedicamento.validarMedicamento(nome, diaSemana, horario, tipo)`.

Em todos os casos, a validação é a primeira instrução do método, antes de qualquer busca, geração de id ou persistência. Se os dados forem inválidos, é lançada `IllegalArgumentException`, interrompendo o fluxo.

O validador é instanciado como dependência interna do próprio service (campo `final`, criado no construtor), sem passar a ser injetado externamente, já que não possui estado nem depende de portas externas.

## Alternativas consideradas

### Alternativa 1 — Validar diretamente nos services (escolhida)

Instanciar as classes `ValidarDadosUsuario` e `ValidarDadosMedicamento` dentro de cada service e chamá-las no início dos métodos de negócio.

**Vantagens:**
- Reaproveita as classes de validação já existentes, sem duplicar regras.
- Mantém a validação centralizada em um único ponto de entrada por fluxo (registro/edição).
- Baixo custo de implementação e nenhuma mudança nas portas ou interfaces.

**Desvantagens:**
- O service depende diretamente de uma classe concreta de validação, e não de uma abstração/porta.
- Cada service precisa lembrar de chamar a validação manualmente; não há garantia estrutural de que isso sempre aconteça.

### Alternativa 2 — Validar na camada de entrada (controllers/adapters)

Mover a validação para os adapters de entrada, antes mesmo de chamar os services da aplicação.

**Vantagens:**
- Falha rápido, antes mesmo de entrar na camada de aplicação.
- Mantém os services mais enxutos.

**Desvantagens:**
- Os services deixariam de ser seguros quando chamados diretamente (por testes, outros adapters, etc.), pois a garantia dependeria de quem os chama.
- Quebra o princípio de que a camada de domínio/aplicação deve ser autossuficiente e não confiar cegamente na camada externa.

### Alternativa 3 — Validação via Value Objects/construtores dos modelos

Fazer com que `Usuario`, `Medicamento`, etc. validem seus próprios dados no construtor, eliminando a necessidade de classes de validação separadas.

**Vantagens:**
- Impossível construir um objeto de domínio em estado inválido.
- Validação fica mais próxima do modelo que ela protege.

**Desvantagens:**
- Exigiria refatoração maior dos modelos existentes.
- Misturaria responsabilidades de validação de entrada (formato, obrigatoriedade) com a modelagem do domínio.
- Fora do escopo da mudança atual, que é pontual.

## Justificativa

A Alternativa 1 foi escolhida por ser a que resolve o problema imediato — validações existentes e não utilizadas — com o menor impacto na arquitetura atual:

- **Manutenibilidade**: reaproveita código já escrito e testável, sem introduzir novas camadas.
- **Testabilidade**: os services continuam fáceis de testar isoladamente, agora também cobrindo os casos de dados inválidos.
- **Acoplamento**: o acoplamento gerado (service → classe de validação concreta) é aceitável, pois essas classes não têm dependências externas nem efeitos colaterais.
- **Custo/Complexidade**: menor esforço de implementação comparado a mover validação para os adapters ou redesenhar os modelos de domínio.

## Consequências

**Positivas**
- Dados inválidos (nome/e-mail/senha vazios, e-mail mal formatado, campos obrigatórios de medicamento ausentes) não chegam mais às portas de persistência.
- As classes de validação passam a ter utilidade real e cobertura de uso no fluxo de negócio.
- Erros de validação agora são sinalizados de forma explícita via `IllegalArgumentException`, facilitando o tratamento nas camadas superiores.

**Negativas**
- Os services passam a depender de classes concretas de validação em vez de abstrações, o que pode dificultar a troca da estratégia de validação no futuro.
- A validação depende de disciplina do desenvolvedor para ser chamada no início de cada método; não há mecanismo que force isso estruturalmente.
- `ValidarDadosUsuario` mistura uso estático e por instância de forma inconsistente (`ValidarEmail` e `ValidarInformacoesVazias` são estáticas), o que pode gerar confusão em manutenções futuras.

## Impactos

- `RegistrarUsuarioService`
- `EditarUsuarioService`
- `RegistrarMedicamentoService`
- `EditarMedicamentoService`
- Indiretamente, qualquer teste unitário existente para esses services que não previa lançamento de `IllegalArgumentException` para dados inválidos.

## Implementação

1. Importar `ValidarDadosUsuario` em `RegistrarUsuarioService` e `EditarUsuarioService`.
2. Importar `ValidarDadosMedicamento` em `RegistrarMedicamentoService` e `EditarMedicamentoService`.
3. Instanciar o validador correspondente no construtor de cada service, como campo `final`.
4. Chamar o método de validação como primeira instrução dos métodos de negócio (`registrarIdoso`, `registrarFamiliar`, `editarUsuario`, `registrarMedicamento`, `editarMedicamento`).
5. Atualizar/criar testes unitários cobrindo os cenários de dados inválidos para cada service.

## Observações

O nome da classe `ValidarDadosRegistro` foi alterado para `ValidarDadosUsuario` para refletir melhor seu uso, já que ela é chamada tanto no registro quanto na edição de usuário, não apenas no registro.

## Data

14/09/2026

## Responsáveis

- Gilvan Pedro