# ADR-0001 — Adoção da Arquitetura Hexagonal (Ports & Adapters)

## Status

Aceito

## Contexto

O CuidaMed começou como um protótipo em Java puro, com toda a lógica concentrada em poucas classes (`Main`, `NotificacoesApi`, classes de modelo) e sem separação entre regra de negócio e detalhes técnicos.

- **Qual problema precisa ser resolvido?** Definir uma arquitetura antes que o projeto cresça, evitando que regra de negócio e tecnologia fiquem misturadas.
- **Por que esse problema é importante?** O plano do projeto inclui trocar peças técnicas centrais no curto prazo: sair de notificação simulada no console para o Firebase de verdade, e sair de dados criados na mão para persistência real.
- **Limitações e requisitos envolvidos:** o projeto é mantido por uma única pessoa, não há necessidade de escalar partes do sistema de forma independente, e o ambiente de desenvolvimento não deve exigir infraestrutura pesada.
- **Situação atual a modificar:** a lógica de notificação está hoje numa classe (`NotificacoesApi`) sem nenhuma abstração, o que faria qualquer troca futura de tecnologia exigir alterações espalhadas pelo código.

## Decisão

Adotar a Arquitetura Hexagonal (Ports & Adapters) como arquitetura do CuidaMed.

- **O que será utilizado ou alterado?** O código será reorganizado em `domain/` (modelos e portas), `application/` (serviços que implementam as portas de entrada) e `adapter/` (implementações concretas de entrada e saída).
- **Como a solução será aplicada?** Cada dependência externa (notificação, persistência, forma de entrada) passa a se comunicar com o domínio apenas através de interfaces (portas), nunca diretamente.
- **Por que essa alternativa foi escolhida?** Porque isola exatamente as partes que já sabemos que vão trocar de tecnologia em breve, sem adicionar a complexidade de infraestrutura que um projeto desse porte não precisa.

## Alternativas consideradas

### Alternativa 1 — Monolito em camadas tradicional

Organização clássica em camadas (apresentação, negócio, persistência), com dependência direta entre elas.

**Vantagens:**
- Simples de entender e de começar.
- Menos arquivos e interfaces no início.

**Desvantagens:**
- Trocar tecnologia (ex: console por Firebase) tende a vazar mudanças pra dentro da regra de negócio.
- Dificulta testar a lógica de negócio isolada de banco de dados ou serviços externos.

### Alternativa 2 — Arquitetura Hexagonal (Ports & Adapters)

Domínio isolado no centro, comunicando-se com o exterior via portas e adaptadores.

**Vantagens:**
- Troca de tecnologia (notificação, persistência) não exige alterar a regra de negócio.
- Regra de negócio testável sem depender de infraestrutura externa.

**Desvantagens:**
- Mais boilerplate inicial (interfaces de porta) do que um CRUD direto.
- Exige disciplina pra não deixar detalhes técnicos vazarem pro domínio.

### Alternativa 3 — Microsserviços

Divisão do sistema em serviços independentes, cada um com seu próprio deploy e, possivelmente, seu próprio banco.

**Vantagens:**
- Permite escalar partes do sistema de forma independente.
- Times diferentes podem trabalhar em paralelo sem conflito.

**Desvantagens:**
- Exige infraestrutura (gateway, service discovery, comunicação de rede) incompatível com um projeto mantido por uma pessoa.
- Aumenta drasticamente a complexidade operacional sem nenhum ganho real nesse estágio do projeto.

## Justificativa

- **Manutenibilidade:** hexagonal isola pontos de troca conhecidos (notificação, persistência), reduzindo o custo de manutenção quando essas trocas acontecerem.
- **Complexidade:** menor que microsserviços, compatível com o tamanho atual do projeto.
- **Testabilidade:** o domínio pode ser testado com implementações falsas das portas, sem subir banco ou serviço externo.
- **Acoplamento:** reduzido entre regra de negócio e tecnologia, já que o domínio só conhece interfaces.
- **Facilidade de desenvolvimento:** ligeiramente menor no início (mais arquivos), mas compensada pela flexibilidade nas próximas fases do projeto.
- **Compatibilidade com o projeto:** encaixa com o cenário real — dev solo, escopo pequeno, mas com trocas de tecnologia já mapeadas no roadmap.

## Consequências

**Positivas**
- Trocar `ConsoleNotificacaoAdapter` por `FirebaseNotificacaoAdapter` no futuro não exige tocar na regra de negócio.
- A lógica de negócio pode ser testada isoladamente.
- Estrutura clara de onde cada tipo de código deve ficar.

**Negativas**
- Mais arquivos e interfaces para gerenciar desde o início.
- Curva de aprendizado inicial maior do que uma estrutura em camadas simples.

## Impactos

Afeta a organização geral do código-fonte: todas as classes existentes (`Usuario`, `Idoso`, `Familiar`, `Medicamento`, `TipoMedicamento`, `HistoricoMedicamento`, `NotificacoesApi`, `Main`) precisam ser realocadas dentro da nova estrutura de pastas (`domain`, `application`, `adapter`, `config`).

## Implementação

1. Criar os pacotes `domain/model`, `domain/port/in`, `domain/port/out`, `application/service`, `adapter/in`, `adapter/out` e `config`.
2. Mover as classes de modelo existentes para `domain/model`, sem alteração de conteúdo.
3. Extrair as interfaces de porta (`NotificarPort`, portas de salvar) a partir do comportamento já existente em `NotificacoesApi`.
4. Recriar `NotificacoesApi` como `ConsoleNotificacaoAdapter`, implementando `NotificarPort`.
5. Mover o cenário de teste do `Main` para `adapter/in/console`, ajustando as chamadas para passar pelas portas de entrada.

## Observações

Esta decisão é a base estrutural para as demais ADRs do projeto — as decisões seguintes (notificação via console, persistência via portas) só fazem sentido dentro dessa arquitetura.

## Data

12/09/2026

## Responsáveis

- Gilvan Pedro