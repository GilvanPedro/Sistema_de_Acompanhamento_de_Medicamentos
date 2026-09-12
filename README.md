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

## Tecnologias

- Java 17
- Maven

## Estrutura do projeto

```
Sistema_de_Acompanhamento_de_Medicamentos/
└── Gerenciador de Medicamentos/
    ├── pom.xml
    └── src/main/java/br/com/
        ├── Main.java
        ├── api/
        │   └── NotificacoesApi.java
        └── model/
            ├── Usuario.java
            ├── Idoso.java
            ├── Familiar.java
            ├── Medicamento.java
            ├── TipoMedicamento.java
            └── HistoricoMedicamento.java
```

## Como rodar o projeto

### Pré-requisitos
- Java 17+
- Maven

### Rodando

```bash
cd "Gerenciador de Medicamentos"
mvn compile exec:java -Dexec.mainClass="br.com.Main"
```

Isso vai rodar o cenário de teste que está na classe `Main` e imprimir no console os dados simulados de idosos, familiares e o aviso de remédio esquecido.

## Próximos passos

- Persistência dos dados (hoje tudo é criado na mão dentro do `Main`)
- API para expor as funcionalidades
- Integração real com Firebase para notificações
- Interface para idosos e familiares

## Licença

Este projeto está sob a licença presente no arquivo `LICENSE`.
