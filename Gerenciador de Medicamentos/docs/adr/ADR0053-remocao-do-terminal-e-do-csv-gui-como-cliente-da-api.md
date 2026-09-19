# ADR-0053 — Remoção do terminal e do CSV; a interface gráfica passa a usar a API

## Status

Aceito

## Contexto

Depois do ADR-0047 o sistema tinha **três portas de entrada** e **duas persistências**:

- a API (usada pelo app Android), só com PostgreSQL;
- a interface gráfica e o terminal, falando direto com o banco (ou, sem `DATABASE_URL`, com arquivos CSV).

Isso trazia problemas reais: a **credencial do banco** precisava estar em cada computador que rodasse a GUI; as regras que só existem na API (limite de tentativas de login, aceite da política de privacidade, avisos por push, confirmação de senha em ações sensíveis) **não valiam** para a GUI nem para o terminal; e o mesmo comportamento tinha de ser mantido em três lugares. O modo CSV também não tinha o aceite de vínculo, então era um caminho com menos garantias.

## Decisão

### 1. O que foi removido

- **Terminal** inteiro (`adapter/in/console`), o agendador de atraso local (`adapter/in/scheduler`), o adaptador de notificação por console e a porta `NotificarPort`, com o `VerificarAtrasoMedicamentoService` (o aviso de atraso agora é do servidor: ADR-0051).
- **Persistência em CSV**: os três adaptadores, o gerador de ids por arquivo, os utilitários de leitura/escrita de CSV e a exceção de linhas corrompidas.
- `AppConfig`, que escolhia entre CSV e PostgreSQL. Não há mais escolha: só PostgreSQL, e só a API acessa o banco.

### 2. A GUI é um cliente da API

- `gui/api/ClienteApi` usa `java.net.http` e Jackson e fala com a mesma API do app: login, renovação automática do token (uma vez, com trava, porque o token de renovação vale uma única vez), remédios, tomadas, avisos, vínculos, política, exportação e exclusão. As mensagens de erro vêm do servidor, já em português (`ApiException`, com o status; status 0 é falta de conexão).
- Modelos próprios da GUI (`Conta`, `Remedio`, `Aviso`, `Tomada`, `Pedido`). **Nenhuma tela importa serviços, portas, persistência ou o modelo de domínio.** As únicas exceções são os enums `TipoMedicamento` e `TipoNotificacao` (só listas de valores) e o leitor de configuração `Ambiente`. Um teste (`GuiSoFalaComAApiTest`) falha se alguém importar mais que isso.
- **Rede sem travar a janela:** as telas buscam os dados em segundo plano (`Navegador.carregar` e `Navegador.fazer`, sobre `SwingWorker`) e mostram "Carregando…". Um contador de "geração" descarta a resposta que chega depois de a pessoa já ter ido para outra tela. Se a sessão acabar, volta para o início com um aviso; outros erros mostram o motivo com "Tentar de novo".
- O endereço da API é o do Render por padrão e pode ser trocado com `CUIDAMED_API_URL`.

### 3. O que mudou para quem usa a GUI

- O vínculo é pedido **só por e-mail** (antes aceitava também o id): a API não revela se a conta existe, então a mensagem é a do servidor.
- Trocar e-mail ou senha, baixar os dados e excluir a conta pedem a **senha atual**.
- Novas telas e ações da LGPD (ADR-0052): aceite no cadastro, tela "Antes de continuar", "Baixar meus dados" e "Excluir minha conta".
- A GUI agora precisa de **internet e da API no ar**. O agendador do ADR-0051 mantém o servidor acordado.

### 4. Testes

- `ClienteDaGuiTest`: o cliente contra o servidor de verdade (porta aleatória, portas em memória), cobrindo o fluxo idoso/familiar, erros, renovação do token, exportação e exclusão.
- `TelasDaGuiTest`: monta todas as telas com dados de exemplo, sem abrir janela (com `-Dcuidamed.fotos=PASTA` também desenha cada uma em PNG, para conferir o visual).
- O mesmo fluxo foi rodado uma vez contra o servidor no ar, e as contas de teste foram apagadas.

## Consequências

- **A credencial do banco existe só no servidor.** Quem roda a GUI não precisa de `DATABASE_URL`.
- Uma regra nova na API vale para o app e para a GUI sem código extra.
- Menos código para manter: o projeto perdeu cerca de 1 800 linhas de terminal e CSV.
- Os ADRs antigos sobre CSV e terminal continuam como registro histórico, marcados como **substituídos por este**.
- **Pendências:** a GUI ainda vai no mesmo projeto Maven do servidor (só a convenção e o teste a separam); o passo seguinte natural é um módulo próprio. A janela da GUI foi verificada por testes e desenho das telas, e vale uma passada de uso real.

## Nota

O ADR-0045 (rascunho de planejamento, que dizia que seria excluído) foi removido, como combinado. As decisões que ele antecipava estão nos ADRs 0046 a 0053.
