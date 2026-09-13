# Sistema_de_Acompanhamento_de_Medicamentos

Protótipo de backend de um sistema pensado para ajudar idosos e seus familiares a não esquecerem de tomar os medicamentos na hora certa.

A ideia por trás é simples: cada idoso tem um ou mais familiares vinculados, cada medicamento tem um horário e um dia da semana marcados, e o sistema simula o aviso — pro idoso, na hora de tomar, e pro familiar, quando o remédio for ou não tomado.

Neste estágio o projeto ainda é só a modelagem em Java puro rodando no console, sem banco de dados, API ou interface — é a base da lógica antes de virar algo maior.

## O que já está pronto

- **Modelagem de usuários** com herança: `Usuario` como base, e `Idoso` e `Familiar` estendendo essa classe
- **Vínculo entre idoso e familiar** — um idoso pode ter vários familiares responsáveis, e um familiar pode acompanhar vários idosos
- **Cadastro de medicamentos** (`Medicamento`), com nome, horário (`LocalTime`), dia da semana (`DayOfWeek`) e tipo (`TipoMedicamento`: comprimido, gotas, injeção ou outro)
- **Histórico de medicamentos** (`HistoricoMedicamento`), guardando se o medicamento foi ou não tomado, e em que data e hora
- **Simulação de notificações** (`NotificacoesApi`), com três avisos possíveis: lembrete pro idoso, aviso de remédio tomado e aviso de remédio esquecido — por enquanto tudo impresso no console
- **Classe `Main`** com um cenário de teste montando idosos, familiares e medicamentos na mão, só pra validar se a lógica funciona

## Estrutura Futura do Projeto (Arquitetura Hexagonal)

O projeto segue o padrão **Ports & Adapters (Hexagonal)**: a regra de negócio fica isolada no centro, sem depender de banco de dados, frameworks ou forma de entrada/saída. Tudo que é "externo" se conecta através de contratos (portas) e implementações trocáveis (adaptadores).

```
Gerenciador de Medicamentos/
│
├── src/main/java/br/com/
│   │
│   ├── domain/                              
│   │   ├── model/
│   │   │
│   │   └── port/
│   │       ├── in/
│   │       │   
│   │       └── out/
│   │
│   ├── application/
│   │   └── service/
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── console/    
│   │   │   │     
│   │   │   └── web/                         
│   │   │
│   │   └── out/
│   │       ├── notification/
│   │       │   
│   │       └── persistence/                  
│   │
│   └── config/
│
└── pom.xml
```

### `domain/model/`
Aqui ficam as classes que representam o negócio de verdade: `Usuario`, `Idoso`, `Familiar`, `Medicamento`, `TipoMedicamento`, `HistoricoMedicamento`. São classes puras — sem anotação de banco, sem import de framework, sem nada que amarre com tecnologia. Só a lógica e os dados que fazem sentido pro problema em si.

### `domain/port/in/`
As **portas de entrada**: interfaces que descrevem o que o sistema é capaz de fazer, do ponto de vista de quem usa. Exemplo: `RegistrarMedicamentoUseCase`, `RegistrarTomadaUseCase`. Não importa se quem chama é um console, uma API REST ou um app mobile — a porta é sempre a mesma.

### `domain/port/out/`
As **portas de saída**: interfaces que descrevem o que o sistema *precisa* que alguém faça por ele, mas sem dizer como. Exemplo: `NotificarPort` (precisa notificar alguém, não importa se é console, e-mail ou Firebase), `SalvarMedicamentoPort` (precisa persistir, não importa se é memória, SQLite ou Postgres).

### `application/service/`
A implementação das portas de entrada. É onde a lógica de negócio de fato acontece — orquestra os models, chama as portas de saída quando precisa notificar ou salvar algo. Exemplo: `RegistrarTomadaService` implementando `RegistrarTomadaUseCase`.

### `adapter/in/`
As implementações concretas de "como o mundo de fora aciona o sistema". Cada subpasta é uma forma diferente de entrada:
- `console/` — o cenário de teste rodando na mão (o `Main.java` de hoje)
- `web/` — futuro: controllers de uma API REST

### `adapter/out/`
As implementações concretas de "como o sistema fala com o mundo de fora". Cada subpasta é uma tecnologia diferente:
- `notification/` — hoje o `ConsoleNotificacaoAdapter` (imprime no console); amanhã pode entrar o `FirebaseNotificacaoAdapter`
- `persistence/` — ainda não existe, mas é onde entrará a implementação com banco de dados (SQLite, Postgres, Firestore, o que for escolhido)

### `config/`
Onde as peças são montadas: qual adaptador concreto vai ser usado pra cada porta. É a única parte do sistema que "conhece" tanto o domínio quanto os adaptadores ao mesmo tempo — o resto do código nunca sabe qual implementação está rodando por trás da interface.

---

**Regra de ouro:** as dependências sempre apontam para dentro. `domain/` não conhece ninguém. `application/` conhece só o `domain/`. `adapter/` conhece o `domain/` (pra implementar as portas) mas o `domain/` nunca conhece o `adapter/`.

## Como rodar o projeto

### Pré-requisitos
- Java 17+
- Maven

### Rodando

```bash
cd "Gerenciador de Medicamentos"
mvn compile exec:java -Dexec.mainClass="br.com.adapter.in.console"
```

Isso vai rodar o cenário de teste que está na classe `Main` e imprimir no console os dados simulados de idosos, familiares e o aviso de remédio esquecido.

## Próximos passos

- Persistência dos dados (hoje tudo é criado na mão dentro do `Main`)
- API para expor as funcionalidades
- Integração real com Firebase para notificações
- Interface para idosos e familiares

## Licença

Este projeto está sob a licença presente no arquivo [LICENSE](LICENSE).
