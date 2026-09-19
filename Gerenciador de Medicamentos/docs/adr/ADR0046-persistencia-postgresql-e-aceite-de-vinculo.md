# ADR-0046 — Persistência em PostgreSQL (Neon) e aceite do vínculo pelo idoso

## Status

Aceito

## Contexto

Até aqui os dados ficavam em arquivos CSV locais, o que impede usar o sistema de mais de um lugar (a ideia é ter um app Android e o desktop compartilhando os mesmos dados). O planejamento (ADR-0045, rascunho) definiu:

- Trocar o CSV por um banco online, gratuito e sempre disponível.
- Exigir que o idoso aceite o vínculo com um familiar antes de ser acompanhado (consentimento, ligado à LGPD, já que medicamentos são dados de saúde).

Este ADR registra o que foi de fato implementado nessa primeira etapa.

## Decisão

### 1. PostgreSQL no Neon como persistência padrão

- Banco **PostgreSQL** hospedado no plano gratuito da **Neon**. Foi escolhido no lugar de MySQL porque há mais opções gratuitas de PostgreSQL, e a troca só afeta o adaptador de persistência.
- **Schema** em `src/main/resources/db/migration/V1__criar_tabelas.sql`, aplicado manualmente pelo SQL Editor da Neon. O nome segue o padrão do Flyway, mas o Flyway ainda não é usado. Tabelas:
  - `usuario`: uma tabela para idoso e familiar, com a coluna `tipo`. E-mail único sem diferenciar maiúsculas de minúsculas, entre contas não excluídas. Senha só como hash BCrypt.
  - `vinculo`: liga idoso e familiar, com `status` (`PENDENTE`, `ACEITO`, `RECUSADO`), `solicitado_em` e `respondido_em`.
  - `medicamento`: dia da semana e tipo como texto, com `CHECK` nos valores do `DayOfWeek` e do `TipoMedicamento`.
  - `historico`: só recebe inserções.
- **Exclusão lógica** em `usuario` e `medicamento` (`excluido_em`), e colunas `criado_em` e `atualizado_em`, preparadas para a sincronização do app offline (regra "excluir vence editar" do ADR-0045). O histórico é apagado de fato quando removido, e os vínculos de um usuário excluído são removidos.
- **Adaptadores** em `adapter/out/persistence/postgres/`: `UsuarioPostgresAdapter`, `MedicamentoPostgresAdapter` e `HistoricoPostgresAdapter`. Implementam as **mesmas portas** de `domain/port/out` que os adaptadores CSV, então `domain/` e `application/` não precisaram mudar para isso. Usam JDBC com `PreparedStatement` (sem concatenar valores no SQL) e um pool **HikariCP** (5 conexões, nenhuma ociosa, porque a Neon suspende o banco sem uso). Dependências novas: driver `postgresql` 42.7.4 e `HikariCP` 5.1.0.
- **Geração de ids:** `GerarIdPostgresAdapter` pega o próximo id da sequência da coluna `id` (`nextval(pg_get_serial_sequence(...))`). Os serviços continuam gerando o id antes de salvar, como antes, e funciona com vários clientes ao mesmo tempo.
- **Configuração** em `ConexaoPostgres`: lê `DATABASE_URL` (no formato que a Neon fornece, `postgresql://usuario:senha@host/banco?sslmode=require`, convertido para JDBC) ou `DB_URL`, `DB_USER` e `DB_PASSWORD`. Primeiro procura na variável de ambiente e, se não achar, no arquivo `.env` na raiz do repositório. O `.env` está no `.gitignore` e existe um `.env.example` com valores de mentira. A senha nunca fica no código nem no Git.
- **Escolha da persistência** no `AppConfig`: se `DATABASE_URL` (ou `DB_URL`) estiver definida, usa o PostgreSQL; senão, continua nos CSV. Ao iniciar, o programa escreve no console qual persistência está usando (`[CuidaMed] Persistência: ...`), para evitar o engano de achar que está no banco e estar nos arquivos.

### 2. PostgreSQL vira o padrão; o CSV não recebe novos recursos

Novos recursos (aceite do vínculo agora, sincronização depois) existem só no PostgreSQL. O CSV continua funcionando para o que já fazia, mas sem eles.

### 3. Aceite do vínculo pelo idoso

- **Novos elementos:** `StatusVinculo` e `PedidoVinculo` (em `domain/model`), a porta `GerenciarVinculoCase` e o serviço `GerenciarVinculoService`.
- **Familiar pede, idoso responde:** o familiar informa o e-mail do idoso e o vínculo fica `PENDENTE`. O idoso vê os pedidos e aceita ou recusa. Só vínculos `ACEITO` fazem o familiar ver o idoso, e só eles entram em `getFamiliares()` e `getIdosos()`.
- **Validade de 24 horas:** um pedido pendente mais antigo que isso conta como inexistente. Quem decide é o **relógio do banco** (`now()`), e não o do aparelho de cada pessoa.
- **Regras:** não é possível pedir de novo se já há vínculo aceito ou pedido pendente válido. Um pedido recusado ou expirado pode ser refeito. O idoso pode **remover** um familiar já vinculado.
- **O idoso pode adicionar um familiar direto**, e o vínculo já nasce `ACEITO`, porque o próprio ato é o consentimento dele.
- **Portas:** os novos métodos foram acrescentados a `SalvarUsuarioPort` como `default`. No CSV, listar pedidos devolve lista vazia e buscar status devolve nulo; pedir, responder e remover lançam `UnsupportedOperationException` ("O aceite de vínculo exige o banco PostgreSQL"), e a GUI mostra essa mensagem.
- **Interfaces:**
  - GUI: `TelaVinculo` mostra os pedidos (Aceitar e Recusar), os familiares com "Remover" (com confirmação) e o campo para adicionar ou pedir. A tela inicial do idoso mostra um cartão quando há pedidos pendentes.
  - Terminal: o familiar passa a "Pedir para acompanhar um idoso". O idoso ganha as opções 5 (pedidos) e 6 (remover familiar), e um aviso ao entrar.

## Alternativas consideradas

### Alternativa 1 — Manter só o CSV
**Vantagens:** nada a configurar, funciona offline no computador.
**Desvantagens:** não compartilha dados entre aparelhos; o status do vínculo e a sincronização ficam difíceis de guardar.

### Alternativa 2 — Manter CSV e PostgreSQL com todos os recursos nos dois
**Vantagens:** o modo sem banco continuaria completo.
**Desvantagens:** o dobro de trabalho e risco de os dois comportamentos divergirem.

### Alternativa 3 — Aplicar o schema com Flyway na inicialização
**Vantagens:** reprodutível, sem passo manual.
**Desvantagens:** exige a credencial do banco com permissão de alterar o schema em toda execução. Adiado; o arquivo já segue o nome do Flyway, então dá para adotar depois.

### Alternativa 4 — PostgreSQL no Supabase
**Vantagens:** traz autenticação e armazenamento prontos.
**Desvantagens:** o plano gratuito pausa o projeto após uma semana sem atividade. A Neon suspende só a computação, e volta em milissegundos.

## Justificativa

- **Manutenibilidade e acoplamento:** a arquitetura hexagonal permitiu trocar a persistência escrevendo apenas novos adaptadores.
- **Custo:** Neon e Firebase têm planos gratuitos suficientes para o projeto neste estágio.
- **Segurança:** consultas parametrizadas, credenciais fora do código e do Git, e nenhuma senha em texto puro.
- **Preparação:** exclusão lógica, datas de atualização e ids de sequência já servem à futura sincronização do app Android.

## Consequências

**Positivas**
- Os dados agora ficam num banco online, compartilhável entre computadores e, depois, o app.
- O idoso controla quem o acompanha.
- Nenhuma alteração em `domain/` para a persistência; o aceite do vínculo acrescentou modelos, porta e serviço novos.

**Negativas**
- **Sem testes automatizados:** o projeto ainda não tem nenhum. A verificação foi manual, pelo responsável, usando a GUI com o banco ligado; cadastro, medicamentos e o fluxo de aceite foram exercitados e funcionaram.
- **Credencial do banco no computador:** a GUI e o terminal falam direto com o banco, então quem os executa precisa da string de conexão. É aceitável só por enquanto; a API REST (próxima etapa) resolve isso, deixando a credencial apenas no servidor.
- **Primeira conexão lenta** depois de o banco ficar suspenso, por causa da suspensão da Neon (até `connectionTimeout` de 30 s).
- O modo CSV fica com o aceite de vínculo indisponível.
- **Dados antigos dos CSV não foram migrados;** o banco começou vazio. Ids gerados antes no CSV não entram na sequência.
- `default` em `SalvarUsuarioPort` que lançam exceção é uma solução transitória, para o CSV compilar sem implementar o que não suporta.

## Impactos

- `pom.xml`: driver PostgreSQL e HikariCP.
- `config/AppConfig`: escolhe a persistência e cria `GerenciarVinculoService`.
- `domain/port/out/SalvarUsuarioPort`: métodos de vínculo com aceite.
- `domain/model`: `StatusVinculo` e `PedidoVinculo`.
- `application/service/GerenciarVinculoService` e `domain/port/in/GerenciarVinculoCase`.
- `adapter/out/persistence/postgres/` e `adapter/out/id/GerarIdPostgresAdapter`.
- `adapter/in/gui` (`TelaVinculo`, `TelaHomeIdoso`, `Rotulos`) e `adapter/in/console` (`TelaIdoso`, `TelaFamiliar`).
- `.gitignore` (`.env`), `.env.example` e o schema em `src/main/resources/db/migration/`.
- Este ADR revisa parte do ADR-0003 (vínculo idoso-familiar), que previa vínculo direto, sem aceite.

## Implementação

1. Criar o projeto e o banco na Neon e aplicar `V1__criar_tabelas.sql` no SQL Editor.
2. Guardar a string de conexão no arquivo `.env` (fora do Git).
3. Rodar a GUI ou o terminal; o console confirma `Persistência: PostgreSQL`.

## Observações

- Próximos passos (ADR-0045): API REST com login por token, app Android em Kotlin com uso offline, notificações push, distribuição por APK e cuidados de LGPD.
- Pendências conhecidas: migrar (ou descartar) os dados antigos dos CSV, adotar Flyway, e testes automatizados.

## Data

19/09/2026

## Responsáveis

- Gilvan Pedro
