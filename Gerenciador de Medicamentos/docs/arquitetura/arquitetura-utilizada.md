## Arquitetura utilizada: Hexagonal (Ports & Adapters)

O CuidaMed é organizado seguindo o padrão **Ports & Adapters**, também conhecido como Arquitetura Hexagonal. A ideia central é simples: a regra de negócio fica isolada no centro do sistema, e tudo que é externo — banco de dados, forma de notificação, forma de entrada dos dados — se conecta a ela através de contratos (as *ports*) e implementações trocáveis (os *adapters*).

Na prática, isso significa que o `domain/` não sabe se as notificações saem pelo console ou pelo Firebase, e não sabe se os dados são salvos em memória, num arquivo ou num banco de verdade. Ele só conhece interfaces. Quem decide qual implementação usar fica isolado numa camada de configuração, sem essa decisão vazar pro resto do código.

## Por que essa arquitetura e não outra

Antes de bater o martelo, o projeto foi comparado contra duas alternativas comuns: um **monolito em camadas tradicional** e uma **arquitetura de microsserviços**. A escolha não foi por modismo — veio de olhar pro que o CuidaMed realmente precisa neste estágio e pra onde ele vai crescer.

**Contra microsserviços:** o projeto é mantido por uma pessoa, não tem múltiplas equipes disputando espaço, e não existe necessidade de escalar partes do sistema de forma independente. Dividir em serviços separados aqui significaria pagar o preço de infraestrutura (gateway, service discovery, comunicação de rede) sem nenhum dos benefícios que justificam esse preço. Seria complexidade extra sem propósito.

**Contra o monolito em camadas puro:** esse modelo funciona bem quando o sistema é simples e não muda muito. Mas o CuidaMed tem um requisito bem claro logo na frente: hoje as notificações são simuladas no console, e o plano é integrar com o Firebase de verdade. Hoje não existe persistência, e o plano é adicionar um banco de dados. Numa camada tradicional, essas trocas tendem a vazar pra dentro da lógica de negócio, obrigando a mexer no núcleo do sistema toda vez que uma peça externa muda.

**A favor da hexagonal:** ela resolve exatamente esse problema. Cada coisa que pode mudar de tecnologia no futuro (notificação, persistência, forma de entrada) já nasce isolada atrás de uma porta. Trocar o `ConsoleNotificacaoAdapter` pelo `FirebaseNotificacaoAdapter` amanhã não exige tocar em nenhuma linha da regra de negócio — só criar a nova implementação e apontar a configuração pra ela. O mesmo vale quando a persistência entrar em cena.

Tem um ganho extra que pesou na decisão: **testabilidade**. Como a lógica de negócio não depende de nenhuma tecnologia concreta, dá pra testar as regras do CuidaMed sem precisar simular banco de dados ou serviço de notificação nenhum — só passar implementações falsas das portas.

## Resumo da comparação

| Critério | Peso no projeto | Resultado |
|---|---|---|
| Tamanho da equipe (solo) | Alto | Hexagonal e monolito atendem bem; microsserviços é excesso |
| Necessidade de trocar tecnologias (Firebase, banco) | Alto | Hexagonal é a única que isola essa troca de verdade |
| Testabilidade da regra de negócio | Alto | Hexagonal sai na frente por manter o domínio livre de dependências |
| Complexidade de infraestrutura | Alto | Hexagonal continua sendo um único deployável, sem custo extra |
| Velocidade de desenvolvimento agora | Médio | Um pouco mais de boilerplate no início, compensado pela flexibilidade depois |

No fim, a hexagonal ganhou porque encaixa certinho no problema real do CuidaMed: um sistema pequeno hoje, com peças concretas (notificação, persistência) que vão trocar de forma amanhã, e que precisa ficar fácil de testar sem arrastar infraestrutura externa junto.