# ADR-0029 — Verificação de email duplicado via `buscarPorEmail`

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Não havia nenhuma verificação impedindo dois usuários de serem cadastrados (ou editados) com o mesmo email — `RegistrarUsuarioService` e `EditarUsuarioService` aceitavam qualquer email, mesmo já pertencendo a outro usuário.
- **Por que esse problema é importante?** Email é, na prática, o identificador de login do usuário (junto da senha) — permitir duplicidade quebra a premissa básica de que um email identifica uma pessoa só.
- **Limitações e requisitos envolvidos:** a checagem precisa se comportar de forma diferente no cadastro (qualquer duplicidade é erro) e na edição (só é erro se o email já pertencer a **outra** pessoa — o próprio usuário mantendo seu email atual não pode ser tratado como conflito).
- **Situação atual a modificar:** `SalvarUsuarioPort` não tinha nenhum método de busca por email.

## Decisão

Adicionar `buscarPorEmail(String email)` a `SalvarUsuarioPort`, devolvendo o `Usuario` dono daquele email ou `null` se ninguém tiver. Usar esse único método tanto no cadastro (`RegistrarUsuarioService`, onde qualquer resultado não-nulo é erro) quanto na edição (`EditarUsuarioService`, onde só é erro se o id do usuário encontrado for diferente do id de quem está sendo editado).

- **O que será utilizado ou alterado?** Novo método em `SalvarUsuarioPort` e implementação em `UsuarioCsvAdapter`; `RegistrarUsuarioService` e `EditarUsuarioService` passam a checar duplicidade antes de salvar/atualizar.
- **Como a solução será aplicada?** Comparação de email sem diferenciar maiúsculas/minúsculas (`equalsIgnoreCase`), já que email não deveria ser tratado como case-sensitive para fins de identidade de cadastro.
- **Por que essa alternativa foi escolhida?** Um único método (`buscarPorEmail`, devolvendo o usuário completo) serve tanto para "existe?" (cadastro) quanto para "existe e é outra pessoa?" (edição), evitando manter dois métodos fazendo a mesma varredura no arquivo.

## Alternativas consideradas

### Alternativa 1 — `buscarPorEmail`, devolvendo o `Usuario` (ou `null`), usado nos dois contextos

**Vantagens:**
- Um único método cobre tanto o cadastro quanto a edição, sem duplicar a varredura do CSV.
- Devolver o usuário completo (não só um booleano) permite comparar o id, essencial para o caso de edição.

**Desvantagens:**
- Quem só precisa saber "existe ou não" (o caso do cadastro) recebe mais informação do que precisa — irrelevante na prática, já que `!= null` resolve isso numa linha.

### Alternativa 2 — `existeEmail(String email)`, devolvendo `boolean`

Método mais simples, só respondendo sim/não.

**Vantagens:**
- Assinatura mais direta para o caso de uso do cadastro.

**Desvantagens:**
- Insuficiente para a edição, que precisa saber **de quem** é o email para decidir se é conflito real ou o próprio usuário mantendo seu email — exigiria um segundo método (`buscarPorEmail`) de qualquer forma, duplicando a varredura do arquivo.

### Alternativa 3 — Validação de unicidade delegada ao banco de dados (índice único), quando este existir

Adiar essa garantia para quando o projeto migrar de CSV para um banco relacional com constraint de unicidade nativa.

**Vantagens:**
- Bancos relacionais garantem unicidade de forma mais robusta e performática que uma varredura manual.

**Desvantagens:**
- Deixa o sistema vulnerável a emails duplicados enquanto o CSV for a persistência usada (ADR-0006/0009) — sem previsão definida de quando essa migração ocorrerá.

## Justificativa

- **Manutenibilidade:** um único método de busca por email, reaproveitado nos dois serviços que precisam dele.
- **Compatibilidade com o projeto:** resolve o problema com a tecnologia de persistência atual (CSV), sem depender de uma migração futura ainda não planejada.
- **Facilidade de desenvolvimento:** `buscarPorEmail` segue o mesmo padrão já estabelecido por `buscarPorId` (ADR-0016/0019/0021), mantendo consistência de estilo entre os métodos de busca da porta.

## Consequências

**Positivas**
- Impossível cadastrar dois usuários com o mesmo email.
- Edição permite manter o próprio email sem disparar falso positivo de conflito.

**Negativas**
- A checagem ainda depende de percorrer `listarTodos()` internamente — mesmo custo de leitura já aceito em outras buscas por email/nome (ADR-0010).

## Impactos

Afeta `domain/port/out/SalvarUsuarioPort` (novo método), `adapter/out/persistence/UsuarioCsvAdapter` (implementação), `application/service/RegistrarUsuarioService` e `application/service/EditarUsuarioService` (nova checagem antes de salvar/atualizar).

## Implementação

1. Adicionar `buscarPorEmail(String email)` a `SalvarUsuarioPort`.
2. Implementar em `UsuarioCsvAdapter`, comparando com `equalsIgnoreCase`.
3. Em `RegistrarUsuarioService`, lançar `DadosInvalidosException` se `buscarPorEmail(email) != null`.
4. Em `EditarUsuarioService`, lançar a mesma exceção apenas se o usuário encontrado tiver um id diferente do que está sendo editado.
5. Testar: cadastro com email novo (passa), cadastro com email já usado (falha), edição mantendo o próprio email (passa), edição tentando usar o email de outro usuário (falha).

## Observações

Se o projeto migrar para um banco relacional no futuro, essa validação em aplicação pode ser complementada (não substituída) por uma constraint de unicidade no banco, como camada extra de proteção contra condições de corrida que a validação em aplicação sozinha não cobre.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro