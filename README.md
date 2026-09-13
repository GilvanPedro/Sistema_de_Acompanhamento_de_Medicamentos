# Sistema de Acompanhamento de Medicamentos (CuidaMed)

Protótipo de backend de um sistema pensado para ajudar idosos e seus familiares a não esquecerem de tomar os medicamentos na hora certa.

A ideia por trás é simples: cada idoso tem um ou mais familiares vinculados, cada medicamento tem um horário e um dia da semana marcados, e o sistema simula o aviso — pro idoso, na hora de tomar, e pro familiar, quando o remédio for ou não tomado.

Neste estágio o projeto já segue a Arquitetura Hexagonal, com cadastro de usuários e medicamentos validado e com geração automática de id. Ainda não há persistência, API ou interface gráfica — os dados existem só durante a execução, via um cenário de teste no console.

## O que já está pronto

- **Modelagem de usuários** com herança: `Usuario` como base, e `Idoso` e `Familiar` estendendo essa classe
- **Vínculo entre idoso e familiar** — um idoso pode ter vários familiares responsáveis, e um familiar pode acompanhar vários idosos
- **Cadastro de medicamentos** (`Medicamento`), com nome, horário (`LocalTime`), dia da semana (`DayOfWeek`) e tipo (`TipoMedicamento`: comprimido, gotas, injeção ou outro)
- **Histórico de medicamentos** (`HistoricoMedicamento`), guardando se o medicamento foi ou não tomado, e em que data e hora
- **Cadastro validado de usuários e medicamentos**, via `RegistrarUsuarioService` e `RegistrarMedicamentoService`, com verificações de nome, e-mail e dados obrigatórios antes da criação
- **Geração automática de id**, via a porta `GerarIdPort` e o adapter `GerarIdEmMemoriaAdapter` (contador em memória, um por tipo de entidade)
- **Simulação de notificações**, via `NotificarPort` e o adapter `ConsoleNotificationAdapter`, com três avisos possíveis: lembrete pro idoso, aviso de remédio tomado e aviso de remédio esquecido — por enquanto tudo impresso no console
- **`AppConfig`**, montando cada serviço com sua respectiva implementação de porta

## Arquitetura do projeto (Hexagonal / Ports & Adapters)

O projeto segue o padrão **Ports & Adapters (Hexagonal)**: a regra de negócio fica isolada no centro, sem depender de banco de dados, frameworks ou forma de entrada/saída. Tudo que é "externo" se conecta através de contratos (portas) e implementações trocáveis (adaptadores).

```
Gerenciador de Medicamentos/
│
├── src/main/java/br/com/
│   │
│   ├── domain/
│   │   ├── model/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   └── out/
│   │   └── validation/
│   │
│   ├── application/
│   │   └── service/
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── console/
│   │   │   └── web/            (futuro)
│   │   │
│   │   └── out/
│   │       ├── id/
│   │       ├── notification/
│   │       └── persistence/    (futuro)
│   │
│   └── config/
│
└── pom.xml
```

### `domain/model/`
Classes que representam o negócio de verdade: `Usuario`, `Idoso`, `Familiar`, `Medicamento`, `TipoMedicamento`, `HistoricoMedicamento`. São classes puras — sem anotação de banco, sem import de framework, sem nada que amarre com tecnologia.

### `domain/port/in/`
As **portas de entrada**: interfaces que descrevem o que o sistema é capaz de fazer, do ponto de vista de quem usa. Hoje: `RegistrarUsuarioCase`, `RegistrarMedicamentoCase`. Não importa se quem chama é um console, uma API REST ou um app mobile — a porta é sempre a mesma.

### `domain/port/out/`
As **portas de saída**: interfaces que descrevem o que o sistema *precisa* que alguém faça por ele, mas sem dizer como. Hoje: `NotificarPort` (precisa notificar alguém, não importa se é console, e-mail ou Firebase) e `GerarIdPort` (precisa gerar um id, não importa se é um contador em memória ou um banco de dados).

### `domain/validation/`
Regras de validação dos dados de cadastro, independentes de tecnologia: `ValidarDadosRegistro`, `ValidarDadosMedicamento`, `ValidarEmail`, `ValidarInformacoesVazias`. Ficam no domínio porque são regra de negócio pura — não tem nada de console, web ou banco nessas verificações.

### `application/service/`
A implementação das portas de entrada. É onde a lógica de negócio de fato acontece — valida os dados, gera o id e cria o objeto de domínio. Hoje: `RegistrarUsuarioService` e `RegistrarMedicamentoService`.

### `adapter/in/`
As implementações concretas de "como o mundo de fora aciona o sistema":
- `console/` — o cenário de teste rodando na mão (`Main.java`)
- `web/` — futuro: controllers de uma API REST

### `adapter/out/`
As implementações concretas de "como o sistema fala com o mundo de fora":
- `id/` — hoje o `GerarIdEmMemoriaAdapter` (contador em memória); amanhã pode entrar um gerador baseado no CSV ou no banco de dados
- `notification/` — hoje o `ConsoleNotificationAdapter` (imprime no console); amanhã pode entrar o `FirebaseNotificacaoAdapter`
- `persistence/` — ainda não existe; é onde entrará a leitura e escrita dos dados (CSV, como primeira etapa planejada)

### `config/`
Onde as peças são montadas: qual adaptador concreto vai ser usado pra cada porta. É a única parte do sistema que "conhece" tanto o domínio quanto os adaptadores ao mesmo tempo — o resto do código nunca sabe qual implementação está rodando por trás da interface.

---

**Regra de ouro:** as dependências sempre apontam para dentro. `domain/` não conhece ninguém. `application/` conhece só o `domain/`. `adapter/` conhece o `domain/` (pra implementar as portas) mas o `domain/` nunca conhece o `adapter/`.

## Decisões de arquitetura (ADRs)

As decisões arquiteturais do projeto — o que foi decidido, as alternativas consideradas e o porquê — estão documentadas em ADRs, na pasta `docs/adr/`.

## Como rodar o projeto

### Pré-requisitos
- Java 17+
- Maven

### Rodando

```bash
cd "Gerenciador de Medicamentos"
mvn compile exec:java -Dexec.mainClass="br.com.adapter.in.console.Main"
```

Isso vai rodar o cenário de teste que está na classe `Main`, cadastrando idosos, familiares e medicamentos através dos serviços de aplicação, e imprimindo no console as notificações simuladas.

## Próximos passos

- Persistência dos dados em arquivos CSV (hoje tudo existe só durante a execução)
- Serviços de edição (`Atualizar...Service`), separados dos serviços de cadastro
- API para expor as funcionalidades
- Integração real com Firebase para notificações
- Interface para idosos e familiares

## Licença

Este projeto está sob a licença presente no arquivo [LICENSE](LICENSE).
