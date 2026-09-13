# ADR-0006 — Persistência de dados em arquivos CSV

## Status

Proposto

## Contexto

- **Qual problema precisa ser resolvido?** Os dados de usuários, medicamentos e histórico ainda não sobrevivem entre execuções do sistema — tudo é perdido quando o programa termina.
- **Por que esse problema é importante?** Sem persistência, o CuidaMed não pode ser usado de verdade, só demonstrado pontualmente. É preciso guardar os dados de forma simples, sem depender de infraestrutura pesada nesta fase do projeto.
- **Limitações e requisitos envolvidos:** o projeto ainda não tem um banco de dados definitivo escolhido, é mantido por uma única pessoa, e roda localmente sem servidor de banco disponível. Os dados a persistir (usuários, medicamentos, histórico) têm estrutura simples e tabular.
- **Situação atual a modificar:** hoje os objetos de domínio existem apenas em memória, durante a execução do `Main`, sem nenhuma leitura ou escrita em disco.

## Decisão

Persistir os dados do CuidaMed em arquivos CSV, através de implementações das portas de saída já previstas no domínio (`SalvarUsuarioPort`, `SalvarMedicamentoPort`, `SalvarHistoricoPort`).

- **O que será utilizado ou alterado?** Criação de adaptadores em `adapter/out/persistence` que leem e escrevem arquivos `.csv` (um por tipo de dado: usuários, medicamentos, histórico).
- **Como a solução será aplicada?** Cada adaptador implementa a porta correspondente, convertendo os objetos de domínio em linhas de CSV ao salvar, e linhas de CSV em objetos de domínio ao carregar.
- **Por que essa alternativa foi escolhida?** É a forma mais simples de dar persistência real ao sistema agora, sem exigir instalação ou configuração de um banco de dados, mantendo a porta aberta para trocar por um banco relacional depois sem tocar na regra de negócio.

## Alternativas consideradas

### Alternativa 1 — Arquivos CSV

Cada tipo de dado salvo em um arquivo `.csv` próprio, lido e escrito pelos adapters de persistência.

**Vantagens:**
- Não exige instalação de banco de dados nem configuração externa.
- Fácil de inspecionar manualmente durante o desenvolvimento (abre em qualquer editor de texto ou planilha).

**Desvantagens:**
- Sem suporte nativo a relacionamentos (o vínculo idoso-familiar da ADR-0003 precisa ser resolvido na aplicação, não no armazenamento).
- Não lida bem com acesso concorrente nem com grandes volumes de dados.

### Alternativa 2 — Manter apenas em memória (sem persistência real)

Continuar com os dados existindo apenas durante a execução, sem gravação em disco.

**Vantagens:**
- Nenhum esforço de implementação.

**Desvantagens:**
- Sistema continua inutilizável fora de demonstrações pontuais.
- Não avança em nada o objetivo de tornar o CuidaMed usável de verdade.

### Alternativa 3 — Banco de dados relacional (ex: SQLite ou Postgres)

Persistir os dados diretamente num banco relacional desde já.

**Vantagens:**
- Suporte nativo a relacionamentos, consultas mais robustas e melhor comportamento com concorrência.

**Desvantagens:**
- Exige escolher, instalar e configurar um banco antes de conseguir persistir qualquer coisa.
- Maior esforço de implementação neste momento do projeto, para um ganho que ainda não é necessário na fase atual.

## Justificativa

- **Custo:** CSV não tem custo de instalação, configuração ou dependência externa.
- **Facilidade de desenvolvimento:** é a forma mais rápida de sair do "tudo em memória" para "dados que sobrevivem entre execuções".
- **Compatibilidade com o projeto:** mantém a coerência com a Arquitetura Hexagonal (ADR-0001) — a persistência fica atrás das portas já existentes, então trocar CSV por um banco relacional depois não deve exigir mudança nos serviços de aplicação.
- **Manutenibilidade:** arquivos simples de inspecionar e depurar manualmente durante o desenvolvimento.
- **Escalabilidade:** reconhecidamente limitada — é uma solução intermediária, não definitiva, e essa limitação é aceita conscientemente nesta fase.
- **Testabilidade:** os adapters de CSV podem ser testados isoladamente, escrevendo e lendo arquivos de teste, sem afetar os serviços de aplicação.

## Consequências

**Positivas**
- Os dados do CuidaMed passam a sobreviver entre execuções, tornando o sistema utilizável de fato.
- Nenhuma dependência de banco de dados externo é introduzida nesta fase.
- A porta já existente garante que a troca futura por um banco relacional será isolada nos adapters.

**Negativas**
- Sem suporte nativo a relacionamentos — o vínculo idoso-familiar precisa ser resolvido manualmente na leitura dos arquivos.
- Não é adequado para uso concorrente ou para um volume grande de dados.
- Solução reconhecidamente temporária, que exigirá migração quando o projeto crescer.

## Impactos

Afeta `adapter/out/persistence` (novas classes de leitura/escrita de CSV para usuários, medicamentos e histórico), e indiretamente os serviços em `application/service`, que passam a operar sobre dados que realmente persistem entre execuções.

## Implementação

1. Definir o formato de cada arquivo CSV (colunas de `usuarios.csv`, `medicamentos.csv`, `historico.csv`).
2. Implementar os adapters de escrita, convertendo os objetos de domínio em linhas CSV.
3. Implementar os adapters de leitura, reconstruindo os objetos de domínio a partir das linhas CSV, incluindo a reconstrução do vínculo idoso-familiar.
4. Conectar esses adapters às portas `SalvarUsuarioPort`, `SalvarMedicamentoPort` e `SalvarHistoricoPort` via `AppConfig`.
5. Testar o ciclo completo: cadastrar dados, encerrar o programa, reabrir e confirmar que os dados foram recuperados corretamente.

## Observações

Esta é uma solução intermediária. Quando o projeto justificar a migração (mais dados, necessidade de consultas mais robustas ou uso concorrente), uma nova ADR deverá registrar a troca do CSV por um banco de dados definitivo — a estrutura de portas já deixa esse caminho pronto.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro