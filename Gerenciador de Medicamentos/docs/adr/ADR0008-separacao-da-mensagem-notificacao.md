# ADR-0008 — Separação do texto das notificações em uma classe própria

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Hoje o `ConsoleNotificationAdapter` monta o texto de cada notificação (lembrete, remédio tomado, remédio esquecido) diretamente dentro dos próprios métodos, junto com a lógica de exibição (`System.out.println`).
- **Por que esse problema é importante?** Quando o `FirebaseNotificacaoAdapter` for implementado (ADR-0004), se o conteúdo da mensagem for reaproveitado, o texto precisaria ser copiado e colado de um adapter pro outro. Qualquer ajuste de redação (tom, formatação) exigiria mexer dentro da lógica de entrega, em vez de um lugar isolado.
- **Limitações e requisitos envolvidos:** o texto de cada notificação depende de dados do domínio (`Idoso`, `Familiar`, `Medicamento`), mas a forma de montá-lo não deveria estar amarrada a nenhum canal de entrega específico (console, Firebase, e-mail).
- **Situação atual a modificar:** os métodos `lembrarIdoso`, `avisarRemedioTomado` e `avisarRemedioEsquecido` do `ConsoleNotificationAdapter` constroem a string da mensagem e a imprimem na mesma linha de código, misturando "o que dizer" com "como entregar".

## Decisão

Extrair a montagem do texto das notificações para uma classe própria, `MensagensNotificacao`, com métodos estáticos que recebem os dados necessários e devolvem a string pronta. O `ConsoleNotificationAdapter` passa a apenas chamar esses métodos e exibir o resultado.

- **O que será utilizado ou alterado?** Nova classe `MensagensNotificacao` em `adapter/out/notification/`, e simplificação do `ConsoleNotificationAdapter`, que deixa de montar texto e passa só a formatar a saída (imprimir no console).
- **Como a solução será aplicada?** Cada tipo de aviso (lembrete, remédio tomado, remédio esquecido) vira um método estático em `MensagensNotificacao`, recebendo os dados de domínio necessários e devolvendo a `String` já formatada.
- **Por que essa alternativa foi escolhida?** Separa duas responsabilidades que hoje estão coladas — o conteúdo da mensagem e o canal de entrega — sem adicionar complexidade desnecessária pro tamanho atual do projeto.

## Alternativas consideradas

### Alternativa 1 — Classe separada para o texto (`MensagensNotificacao`)

Uma classe com métodos estáticos que só montam e devolvem o texto, sem saber nada sobre como ele será exibido.

**Vantagens:**
- O texto pode ser reaproveitado por outros adapters de notificação (ex: `FirebaseNotificacaoAdapter`) sem duplicação.
- Fácil de testar isoladamente, sem precisar capturar a saída do console.

**Desvantagens:**
- Mais uma classe pra manter, mesmo sendo pequena.

### Alternativa 2 — Manter o texto dentro do próprio adapter (situação atual)

Continuar montando a string diretamente dentro dos métodos do `ConsoleNotificationAdapter`.

**Vantagens:**
- Nenhuma mudança necessária, menos arquivos no projeto.

**Desvantagens:**
- Duplicação garantida assim que um segundo adapter de notificação (ex: Firebase) precisar do mesmo texto.
- Mistura duas responsabilidades diferentes (conteúdo e entrega) na mesma classe.

### Alternativa 3 — Mover o texto para o domínio (`domain/`)

Colocar a montagem das mensagens dentro do próprio `domain/`, por exemplo como método da classe `Idoso` ou `Medicamento`.

**Vantagens:**
- Centralizaria a mensagem junto com os dados que ela descreve.

**Desvantagens:**
- Formatação de texto de notificação não é regra de negócio pura — é um detalhe de apresentação, o que violaria o princípio de manter o domínio livre de preocupações que não são dele.
- Amarraria o domínio a uma forma específica de apresentar mensagens, dificultando trocar esse formato (ex: título + corpo separados) sem tocar nos models.

## Justificativa

- **Manutenibilidade:** ajustes de redação (tom, formatação) passam a acontecer em um único lugar, sem exigir mexer na lógica de entrega.
- **Acoplamento:** reduz o acoplamento entre "o que a mensagem diz" e "como ela é entregue" — o `ConsoleNotificationAdapter` fica responsável só pela exibição.
- **Testabilidade:** `MensagensNotificacao` pode ser testada diretamente, verificando o texto gerado, sem precisar capturar `System.out`.
- **Facilidade de desenvolvimento:** quando o `FirebaseNotificacaoAdapter` for implementado, ele pode reaproveitar os mesmos métodos de `MensagensNotificacao`, evitando copiar e colar texto entre adapters.
- **Compatibilidade com o projeto:** segue o mesmo espírito da Arquitetura Hexagonal (ADR-0001) — cada classe com uma responsabilidade clara e isolada.

## Consequências

**Positivas**
- O `ConsoleNotificationAdapter` fica mais simples, só orquestrando dados e exibição.
- O texto das mensagens pode ser reaproveitado por futuros adapters de notificação sem duplicação.
- Fica mais fácil testar o conteúdo das mensagens isoladamente.

**Negativas**
- Mais uma classe no projeto, ainda que pequena e de baixa complexidade.
- Se o Firebase exigir um formato de mensagem muito diferente (ex: título e corpo separados), `MensagensNotificacao` pode precisar ser revista ou dividida por canal.

## Impactos

Afeta `adapter/out/notification/`: criação da nova classe `MensagensNotificacao` e simplificação do `ConsoleNotificationAdapter`, que passa a delegar a montagem do texto para ela.

## Implementação

1. Criar a classe `MensagensNotificacao` em `adapter/out/notification/`, com um método estático para cada tipo de mensagem (lembrete, remédio tomado, remédio esquecido).
2. Migrar o texto atualmente escrito dentro do `ConsoleNotificationAdapter` para dentro desses métodos, sem alterar o conteúdo da mensagem.
3. Ajustar o `ConsoleNotificationAdapter` para chamar `MensagensNotificacao` e apenas imprimir o texto devolvido.
4. Validar, rodando o `Main`, que as mensagens exibidas continuam idênticas às de antes da mudança.

## Observações

Se, no futuro, o canal de notificação exigir um formato de mensagem estruturalmente diferente (por exemplo, título e corpo separados para uma notificação push), pode fazer sentido dividir `MensagensNotificacao` por canal em vez de manter uma classe única compartilhada — essa decisão deve ser revisitada quando o `FirebaseNotificacaoAdapter` for implementado.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro