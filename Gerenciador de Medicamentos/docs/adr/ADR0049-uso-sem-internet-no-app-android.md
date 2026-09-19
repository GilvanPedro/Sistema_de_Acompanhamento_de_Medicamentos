# ADR-0049 — Uso sem internet no app Android (cópia local, fila de alterações e sincronização)

## Status

Aceito

## Contexto

O app Android usa a API (ADR-0047), que fica no plano gratuito do Render: ele hiberna, e a primeira resposta depois de uma pausa pode levar cerca de um minuto. Além disso, o público (idosos) nem sempre tem internet boa, e um lembrete de remédio ou um "já tomei" não pode depender disso. Ficou combinado desde o planejamento inicial que:

- o app funciona sem internet, e o que a pessoa fizer só é enviado quando houver conexão;
- nos conflitos, **excluir vence editar**, e **entre duas edições vale a que chega por último ao servidor**.

Este ADR registra como isso foi feito.

## Decisão

### 1. Cópia local + fila de alterações ("outbox")

- O app guarda, por pessoa, a **última cópia vinda do servidor** (remédios, histórico, idosos acompanhados e os últimos avisos vistos) e uma **fila de alterações ainda não enviadas**.
- A tela mostra a **cópia do servidor com as alterações pendentes aplicadas por cima** (`Visao`). Assim, o que a pessoa acabou de fazer aparece na hora, com ou sem internet.
- **Leitura:** mostra primeiro o que está guardado e busca no servidor em seguida. Se já existe cópia, espera o servidor no máximo 8 segundos (ele pode estar acordando); depois disso, fica com a cópia. Sem cópia (primeiro uso), espera o servidor de verdade.
- **Escrita** (cadastrar, editar e excluir remédio; marcar "já tomei"): sempre entra na fila e vale na tela na hora. O envio é tentado em seguida. Isso unifica os caminhos: não há um código para "com internet" e outro para "sem internet".
- **Fica online-only:** login, cadastro de conta, perfil, exclusão de conta e vínculos (pedir, aceitar, recusar, remover). Não fazem sentido offline (dependem de outra pessoa ou de senha).

### 2. Operações da fila

`CriarRemedio`, `EditarRemedio`, `ExcluirRemedio` e `RegistrarTomada`, cada uma com um `idOperacao` (UUID). A fila é enviada **uma por vez e na ordem**.

- Um remédio criado sem internet tem um **id provisório negativo**. Quando o cadastro sobe, o id do servidor substitui o provisório nas alterações seguintes da fila.
- **A fila se mantém enxuta:** editar um remédio que ainda nem subiu altera o próprio cadastro pendente; duas edições do mesmo remédio viram uma; excluir um remédio que nem chegou a subir remove tudo o que era dele, sem enviar nada.
- Marcar "já tomei" de novo no mesmo dia é recusado na hora (mesma regra do servidor), sem entrar na fila.
- As validações de cadastro (nome vazio, tamanho) rodam também no aparelho, para avisar na hora.

### 3. Sincronização

- **Quando roda:** logo depois de cada alteração; quando o Android avisa que a rede voltou (`NetworkCallback`, com o app aberto); ao abrir o app; e por um trabalho do WorkManager que exige rede e repete com espera crescente (vale com o app fechado, e é também chamado pela verificação periódica de eventos). Um envio por vez.
- **Falhas de rede e do servidor** (sem conexão, timeout, 429, 5xx) **não descartam nada**: a operação continua na fila.
- **Recusas definitivas** (400, 403, 404) descartam a operação e **avisam o usuário** com o motivo. Exceções: excluir algo já excluído (404) e marcar tomada que o servidor diz já estar registrada contam como feitas, sem aviso.
- **401** (renovação do login recusada) interrompe o envio e leva à tela de entrada.
- Se o cadastro de um remédio for recusado, as alterações que dependiam dele também são descartadas.
- **Resolução de conflitos:** quem aplica é o servidor. Editar um remédio já excluído dá 404 (excluir vence editar, e o app avisa: "foi excluído por outra pessoa"). Duas edições valem na ordem de chegada ao servidor, campo a campo. Tomadas são só inserções e não conflitam.

### 4. Mudanças no servidor (necessárias para isso funcionar)

- **Hora real da tomada.** `POST /medicamentos/{id}/tomadas` aceita `{"dataHora": "..."}`. Sem isso, uma tomada marcada às 8h e enviada às 10h (ou no dia seguinte) seria gravada com a hora do envio, bagunçando o histórico e a regra "uma por dia". O servidor recusa hora **no futuro** (com 5 minutos de tolerância para relógio adiantado) e tomada com **mais de 7 dias**. A regra de "uma por remédio por dia" passa a valer para o dia da hora informada.
- **Idempotência no cadastro de remédio.** `POST /idosos/{id}/medicamentos` aceita o cabeçalho `Idempotency-Key`. O app usa o `idOperacao` da fila como chave. Se o servidor criou o remédio mas a resposta se perdeu (a rede caiu no meio), o reenvio com a mesma chave devolve o remédio já criado (HTTP 200) em vez de criar outro. As chaves ficam na tabela `chave_idempotencia` (`V4__criar_chave_idempotencia.sql`), por usuário, e as de mais de 14 dias são apagadas.
- Editar, excluir e marcar tomada já são naturalmente idempotentes (a tomada pelo índice único por dia, excluir por tratar 404 como feito).

### 5. Armazenamento no aparelho

- **Um arquivo JSON por pessoa, criptografado** (AES-GCM com uma chave do Android Keystore que não sai do aparelho), gravado de forma atômica (arquivo temporário e troca de nome). Se o arquivo não puder ser lido (arquivo danificado, chave perdida), começa vazio e os dados voltam do servidor na próxima busca.
- **Não foi usado o Room** porque ele exige um processador de código (KSP) que não se sabia compatível com o Gradle 9 e o AGP 9 do projeto, e o volume de dados é pequeno (dezenas de remédios e algumas centenas de tomadas). Trocar depois para um banco é possível, pois o acesso está isolado em `ArmazenamentoLocal`.
- **Sessão sem internet:** o app guarda quem foi o último usuário logado. Ao abrir sem internet com login guardado, entra com os dados do aparelho (antes, mostrava uma tela de "sem conexão"). Sem dados guardados, pede conexão.
- **Sair da conta apaga os dados do aparelho**, inclusive alterações ainda não enviadas. O app pede confirmação se houver alterações pendentes.

### 6. Avisos do dia sem internet

Os avisos do idoso (lembrete, tomado, esquecido) são **calculados no aparelho** com as mesmas regras do servidor (tolerância de 10 minutos, virada da meia-noite), usando os remédios guardados e as tomadas, inclusive as ainda não enviadas. Quando há alterações pendentes, o app usa este cálculo mesmo com internet, porque o servidor ainda não sabe delas. Para o **familiar**, os avisos dependem do que o idoso enviou: sem internet, ele vê os últimos que viu, marcados como antigos.

### 7. Interface

Uma faixa no topo das telas mostra "sem conexão", "N alterações aguardando envio" (com botão "tentar enviar agora") e o que foi recusado (com botão "entendi"). Os alarmes de lembrete continuam locais e usam a lista de remédios do aparelho, inclusive os cadastrados sem internet.

## Alternativas consideradas

### Alternativa 1 — Room (SQLite)
**Vantagens:** consultas, migrações de esquema e observação de mudanças prontas.
**Desvantagens:** risco de incompatibilidade do processador de código com as versões do projeto, e mais peso para poucos dados. Fica como evolução se os dados crescerem.

### Alternativa 2 — Só cache de HTTP (OkHttp)
**Vantagens:** quase nenhum código.
**Desvantagens:** não permite escrever sem internet, nem mostrar o que a pessoa acabou de fazer.

### Alternativa 3 — Resolver conflitos no aparelho, com versões ou relógio
**Vantagens:** decisões locais.
**Desvantagens:** o relógio do celular não é confiável, e as regras (excluir vence editar) já se cumprem naturalmente no servidor, que é a fonte da verdade. Foi preferido enviar as operações em ordem e deixar o servidor decidir, avisando o usuário do que foi recusado.

### Alternativa 4 — Chave de idempotência no aparelho, sem mudar o servidor
**Vantagens:** nenhum deploy.
**Desvantagens:** ao reenviar, o app não teria como saber se o remédio já foi criado (a resposta se perdeu) e poderia duplicá-lo. Comparar nome, dia e horário seria frágil.

## Justificativa

- **Confiabilidade:** lembretes e "já tomei" precisam funcionar mesmo com rede ruim, e o servidor gratuito demora a acordar.
- **Correção:** a hora da tomada é a do toque, a fila é enviada em ordem e o reenvio não duplica.
- **Simplicidade:** um único caminho de escrita (fila) e a regra de conflito no servidor.
- **Privacidade:** dados de saúde criptografados no aparelho e apagados ao sair.

## Consequências

**Positivas**
- O app abre e é usável sem internet, e as ações do idoso nunca se perdem por falta de conexão.
- A primeira tela aparece na hora (com a cópia guardada) em vez de esperar o servidor acordar.

**Negativas**
- **O relógio do celular passa a valer para a hora da tomada.** Se estiver muito errado, o servidor recusa (futuro ou mais de 7 dias) e o usuário recebe o aviso.
- **Sem internet por mais de 7 dias, as tomadas mais antigas são recusadas.**
- **A regra de avisos existe duas vezes** (servidor e app), e pode divergir se só uma for alterada. Há testes dos dois lados com os mesmos casos.
- **Sair da conta com alterações pendentes as perde** (com confirmação).
- Ao trocar o id provisório pelo real, os "já tomei" locais de um remédio criado sem internet ficam associados ao id antigo para fins de alarme (caso raro).
- O formato do arquivo local não tem número de versão: novos campos entram com valor padrão, mas uma mudança incompatível exigiria migração.
- Vínculos, login e perfil continuam exigindo internet.
- Um aviso ao familiar continua dependendo de o idoso estar online em algum momento.

## Impactos

- **Servidor:** `RegistrarTomadaService` (hora informada, relógio injetável), `MedicamentoController` (corpo opcional da tomada e `Idempotency-Key`), novo `ChavesDeIdempotencia` (interface e JDBC) e `V4__criar_chave_idempotencia.sql`.
- **App** (`app-android`): pacote `data/local` (modelos da fila, armazenamento criptografado, `Visao`, `HorariosDeRemedio` e `Sincronizador`), `Repositorio` reescrito (leitura local primeiro e escrita pela fila), `Carregador` (mostra a cópia enquanto busca), faixa de conexão nas telas, `SincronizadorWorker` e o retorno da rede no `CuidaMedApp`.
- **Testes:** servidor com 40 testes; app com 44 testes automatizados (fila, sincronização com um servidor de mentira que perde respostas e devolve 404 e 400, o cenário completo sem internet passando pelo repositório, armazenamento e regras de horário). Foi verificado por sabotagem que os testes pegam erros na troca de ids, no descarte indevido e na leitura sem internet.

### Verificado no celular real (Samsung A17, Android 16, contra o servidor no ar)

Com o Wi-Fi e os dados desligados (o `adb` seguia pelo cabo USB): marcar "já tomei", cadastrar um remédio e editar outro. Cada ação apareceu na hora e a faixa passou a contar as alterações. Fechando o app por completo e reabrindo ainda sem internet, ele abriu logado, com os dados e a fila intactos (antes mostrava uma tela de "sem conexão"). Com o servidor tendo excluído, por fora, o remédio que fora editado, ao religar a rede tudo subiu sozinho em cerca de 3 segundos: o remédio novo foi criado uma vez só, a exclusão do servidor prevaleceu, e o app avisou "foi excluído por outra pessoa. A sua alteração não foi aplicada."

Neste teste o servidor no ar ainda era a versão antiga, então a tomada marcada às 15h04 foi gravada às 15h07 (hora do envio). Isso confirma que a hora real da tomada depende do deploy do servidor novo.

## Implementação

1. Executar `V4__criar_chave_idempotencia.sql` no SQL Editor da Neon **antes** de publicar o servidor novo.
2. Enviar o código ao GitHub para o Render publicar.
3. Instalar o app novo no celular.

## Observações

- O servidor antigo continua compatível com o app novo (ignora o corpo da tomada e o cabeçalho), mas sem hora real da tomada e sem proteção contra duplicar remédios.
- Pendências: push instantâneo (Firebase), ícone e APK assinado, versionamento do arquivo local e, se valer a pena, vínculos sem internet.

## Data

19/09/2026

## Responsáveis

- Gilvan Pedro
