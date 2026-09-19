# ADR-0048 — Correções da revisão do projeto (segurança, robustez e avisos de horário)

## Status

Aceito

## Contexto

Com a API no ar (ADR-0047) e o repositório público, foi feita uma revisão do projeto inteiro atrás de bugs. Ela encontrou 13 problemas, agrupados abaixo. Alguns foram confirmados rodando o código (banco inacessível, e-mails rejeitados, projeto e histórico do Git); outros vieram da leitura do código. Este ADR registra o que foi decidido para cada um, na ordem em que foram resolvidos.

## Decisão

### Segurança da API

1. **Senha atual para ações perigosas.** Trocar a senha ou o e-mail (`senhaAtual` no `PATCH /me`) e excluir a conta (`senha` no corpo do `DELETE /me`) passam a exigir a senha atual. Quem só roubou um token de acesso (15 minutos) não tem a senha. Trocar só o nome continua livre. Senha atual errada devolve 403 (e não 401, para o app não tentar renovar o token). As tentativas erradas são limitadas (5 em 15 minutos por usuário), para o token roubado não servir de forma de adivinhar a senha por aqui.
2. **Regra de senha:** de 8 a 72 caracteres (72 bytes é o limite do BCrypt). Vale no cadastro e na troca de senha, em todas as interfaces. Quem já tem conta com senha curta continua conseguindo entrar. Também passaram a ser validados os tamanhos que cabem no banco (nome com até 150 caracteres, e-mail com até 254), que antes davam erro interno.
3. **Limites de tentativas** (`LimiteDeTentativas`, em memória, com tamanho máximo para não estourar a memória):
   - senhas erradas por **e-mail + IP**: 5 em 15 minutos. Assim quem erra é bloqueado sem trancar a dona da conta em outro lugar;
   - falhas de login por **IP**: 30 em 15 minutos, contra quem testa muitos e-mails;
   - contas novas por IP: 10 por hora (configurável), para ninguém encher o banco gratuito;
   - **IP real do cliente:** atrás do proxy do Render, o IP visto pela API era o do proxy (igual para todos), o que anulava o limite. Foi ligado `server.forward-headers-strategy=native`: o cabeçalho `X-Forwarded-For` só é aceito quando o pedido vem de um proxy de rede privada (padrão do Tomcat), então quem chama direto não consegue fingir outro IP.
4. **Cadastro em massa e descoberta de e-mails.** Os pedidos de vínculo por e-mail (`POST /vinculos/pedidos` e `POST /me/familiares`) passam a responder sempre igual (HTTP 202 e a mesma mensagem), exista a conta ou não, seja idoso ou familiar ou não, já haja vínculo ou não. **O cadastro continua dizendo que o e-mail já existe**: sem verificação de e-mail por mensagem, esconder isso deixaria quem se cadastra sem saber por que falhou. Esse risco foi aceito e fica limitado pelo limite de contas novas por IP.

### Robustez

5. **Banco fora do ar na abertura.** O pool passou a não exigir conexão ao ser criado (`initializationFailTimeout=-1`). Antes, um problema de rede na abertura da GUI ou do terminal causava um erro na inicialização de `AppConfig`, seguido de `NoClassDefFoundError` em toda tentativa seguinte, inutilizando o programa até reiniciar. Agora o programa abre e cada operação falha com uma mensagem clara, voltando sozinha quando a conexão voltar. O tempo de espera por conexão caiu de 30 para 15 segundos.
6. **Tomada duplicada.** A checagem "já tomou hoje" e a gravação eram separadas, então dois pedidos ao mesmo tempo (duplo toque, reenvio do app offline) passavam os dois. O banco agora recusa a segunda com um índice único parcial: `V3__uma_tomada_por_remedio_por_dia.sql` (`historico (medicamento_id, data_hora_tomada::date) WHERE foi_tomado`). O adaptador converte a violação em "Você já registrou que tomou X hoje". Um registro "não tomou" no mesmo dia continua permitido.
7. **Leitura por idoso.** A porta `SalvarMedicamentoPort` ganhou `listarPorIdoso`, e o adaptador PostgreSQL filtra no banco. Antes, listar medicamentos, avisos e histórico carregavam a tabela inteira de todos os usuários e filtravam em Java. O padrão da porta serve ao CSV. O agendador de atraso, que de fato precisa de todos, continua lendo tudo.
8. **Avisos repetidos (agendador).** O `VerificarAtrasoMedicamentoService` passa a avisar **uma vez** por horário previsto: um lembrete ao idoso enquanto está dentro da tolerância e um aviso de "esquecido" aos familiares depois dela. Antes, repetiria os dois a cada minuto até a meia-noite. O controle fica em memória (ao reiniciar, pode repetir um aviso). O agendador continua **sem ser iniciado**: só deve ser ligado junto com as notificações push.

### Menores

9. **E-mail:** o final do domínio aceita até 63 letras (antes 6), então `.photography`, `.consulting` e `.technology` deixam de ser recusados.
10. **Meia-noite.** Novo auxiliar `OcorrenciasMedicamento`, usado pelos dois serviços de aviso: um remédio das 23h50 continua contando como atrasado depois da meia-noite, por até 3 horas, e uma tomada feita depois da meia-noite cobre o horário do dia anterior. Os serviços recebem o `Clock` por construtor, o que permite testar horários. Para remédios de outros horários, o comportamento é o mesmo de antes.
11. **Cadastro simultâneo com o mesmo e-mail:** o índice único do banco já impedia o duplicado, mas a resposta era um erro interno; agora vira "Já existe um usuário cadastrado com esse e-mail" (também na edição).
12. **Modo CSV:** nomes com `;` ou quebra de linha (que corromperiam o arquivo) são recusados; a pasta `arquivos/` é criada se não existir; os métodos dos adaptadores CSV passam a ser sincronizados dentro do programa.
13. **Dados pessoais no Git.** O repositório é **público** e os CSVs de `arquivos/` estavam versionados: 14 commits com nomes, e-mails (15 fictícios `@email.com` e 3 de Gmail) e hashes BCrypt. Os quatro arquivos saíram do controle de versão (continuam na máquina de quem os usa) e `arquivos/*.csv` entrou no `.gitignore`. **O histórico antigo continua com esses dados**, por decisão do dono do repositório: os commits são antigos e os CSVs eram apenas dados de teste, então não vale reescrever o histórico e forçar o push (ver Observações).

## Alternativas consideradas

### Alternativa 1 — Bloquear o login por e-mail, de qualquer IP
**Vantagens:** trava também ataques distribuídos.
**Desvantagens:** qualquer pessoa consegue trancar a conta de outra errando a senha de propósito. Foi preferido e-mail + IP, mais um limite por IP.

### Alternativa 2 — Verificação de e-mail no cadastro
**Vantagens:** permitiria esconder se o e-mail existe e barrar contas falsas.
**Desvantagens:** exige envio de e-mail (serviço, domínio, custo). Fica como evolução.

### Alternativa 3 — Confiar em qualquer `X-Forwarded-For`
**Vantagens:** simples.
**Desvantagens:** o cliente poderia fingir outro IP a cada requisição e escapar dos limites. Foi mantido só o proxy de rede privada.

### Alternativa 4 — Exigir senha nova forte (maiúsculas, símbolos etc.)
**Vantagens:** senhas mais difíceis.
**Desvantagens:** incomoda o público idoso sem ganho proporcional. Foi preferido o tamanho mínimo.

## Consequências

**Positivas**
- 33 testes automatizados (eram 13). Foi verificado por sabotagem que eles pegam o erro: ao desfazer de propósito quatro correções (aviso repetido, virada da meia-noite, tamanho mínimo de senha e confirmação da senha atual), 7 testes falharam, e todos voltaram a passar ao restaurar.
- O SQL novo foi verificado num PostgreSQL de verdade, num esquema temporário (depois apagado, sem tocar nas tabelas do projeto): os três scripts de migração, e-mail repetido, `listarPorIdoso`, o índice de tomada única e uma corrida com 8 pedidos simultâneos (1 criado, 7 recusados, 1 linha no banco).
- Banco fora do ar deixa de derrubar a GUI e o terminal.

**Negativas**
- **O app precisa mandar a senha atual** ao trocar senha ou e-mail e ao excluir a conta.
- O `DELETE /me` agora tem corpo; alguns clientes HTTP exigem configuração para isso.
- Os limites ficam em memória: somem ao reiniciar e não valem entre várias instâncias.
- A eficácia do IP real atrás do proxy do Render **não foi verificada de fora** (só se sabe que um cabeçalho forjado não é aceito). Se o proxy do Render não estiver em rede privada, o limite por IP volta a enxergar o IP do proxy.
- O cadastro ainda revela que um e-mail já existe (risco aceito, com limite de contas por IP).
- Cada operação com o banco fora do ar espera até 15 segundos, e a GUI faz as chamadas na thread da tela, então ela trava nesse tempo.
- O aviso repetido do agendador é contido só em memória.

## Impactos

- Novos: `LimiteDeTentativas`, `ConfirmacaoDeSenha`, `LimitesConfig`, `OcorrenciasMedicamento`, `V3__uma_tomada_por_remedio_por_dia.sql` e os testes (`ApiTest`, `LimiteDeCadastroApiTest`, `LimiteDeTentativasTest`, `ValidacoesTest`, `AvisosDeMedicamentoTest`).
- Alterados: validações de usuário, e-mail e medicamento; `AuthController`, `UsuarioController`, `VinculoController` e `MedicamentoController`; `ConexaoPostgres`; adaptadores PostgreSQL e CSV; `SalvarMedicamentoPort`; os serviços de aviso; telas da GUI e do terminal que liam todos os medicamentos; `application.properties`; `.gitignore`.
- Removido: `LimiteDeLogin`, substituído por `LimiteDeTentativas`.

## Implementação

1. Executar `V3__uma_tomada_por_remedio_por_dia.sql` no SQL Editor da Neon (antes, conferir que não há tomadas duplicadas de dias anteriores; o índice não é criado se houver).
2. Enviar o código ao GitHub para o Render publicar a nova versão.
3. (Decidido) Manter o histórico do Git como está.

## Observações

- **Histórico do Git (item 13): mantido como está.** Reescrever o histórico (por exemplo com `git filter-repo --path arquivos --invert-paths`) e forçar o push apaga os dados antigos do repositório, mas muda todos os commits, quebra clones e forks existentes e não apaga cópias que alguém já tenha baixado. Como os e-mails de Gmail e os hashes já estiveram públicos, também vale trocar a senha de qualquer conta real que esteja nesses arquivos.
- Pendências que continuam: limpar tokens de renovação expirados, cobrir os adaptadores PostgreSQL com testes automatizados e, no cadastro do app, consentimento e política de privacidade (LGPD).

## Data

19/09/2026

## Responsáveis

- Gilvan Pedro
