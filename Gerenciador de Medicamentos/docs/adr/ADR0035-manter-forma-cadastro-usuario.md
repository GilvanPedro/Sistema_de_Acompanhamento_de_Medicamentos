# ADR-0035 — Manter escolha explícita de tipo no cadastro (sem inferir por ano de nascimento)

## Status

Aceito

## Contexto

- **Qual problema precisa ser resolvido?** Avaliar se o cadastro deveria continuar exigindo a escolha explícita entre `Idoso` e `Familiar`, ou se deveria adicionar um campo de ano de nascimento e inferir automaticamente o tipo a partir da idade calculada.
- **Por que esse problema é importante?** A forma como o tipo é determinado afeta diretamente a modelagem central do domínio (ADR-0002) — decidir isso errado significa ou impedir cadastros legítimos, ou exigir retrabalho de modelagem mais tarde.
- **Limitações e requisitos envolvidos:** dentro do CuidaMed, `Idoso` não representa "pessoa idosa" no sentido literal — representa "a pessoa cujos medicamentos são rastreados". `Familiar` representa "quem acompanha e recebe avisos". Essa distinção é sobre papel no cuidado, não sobre idade.
- **Situação atual a modificar:** nenhuma — o cadastro já exige escolha explícita (`registrarIdoso`/`registrarFamiliar`) desde a modelagem original (ADR-0002); esta ADR confirma e documenta essa decisão diante da alternativa considerada.

## Decisão

Manter o cadastro com escolha explícita de tipo (`Idoso` ou `Familiar`), sem adicionar ano de nascimento como critério que decide automaticamente qual dos dois papéis o usuário assume.

- **O que será utilizado ou alterado?** Nada no código muda — esta ADR documenta a decisão de não seguir pela alternativa da idade automática, evitando que a questão precise ser rediscutida do zero no futuro.
- **Como a solução será aplicada?** N/A — decisão de manter o comportamento atual.
- **Por que essa alternativa foi escolhida?** Porque o papel (idoso/familiar) é sobre função no cuidado, não sobre idade cronológica — inferir automaticamente por ano de nascimento impediria cenários legítimos onde a idade não corresponde ao papel.

## Alternativas consideradas

### Alternativa 1 — Manter escolha explícita de tipo no cadastro (situação atual)

**Vantagens:**
- Reflete corretamente que o papel no sistema é uma escolha de função (quem cuida vs. quem é cuidado), não uma característica demográfica.
- Cobre cenários legítimos que a idade sozinha não cobriria: uma pessoa de meia-idade com uma condição que exige rastreamento de medicação sendo a pessoa cuidada; um familiar mais velho acompanhando um idoso ainda mais velho.

**Desvantagens:**
- Depende da pessoa escolher corretamente o próprio papel no momento do cadastro — não há nenhuma validação que pegue um erro de escolha.

### Alternativa 2 — Adicionar ano de nascimento e inferir o tipo automaticamente a partir de uma idade de corte

**Vantagens:**
- Removeria a necessidade de a pessoa escolher manualmente o tipo.

**Desvantagens:**
- Impediria cadastros legítimos onde a idade não corresponde ao papel real (pessoa mais nova sendo a cuidada, familiar mais velho que o idoso que acompanha).
- Confunde dois conceitos diferentes — "papel no sistema" e "faixa etária" — que não são a mesma coisa dentro do domínio do CuidaMed.

### Alternativa 3 — Escolha explícita de tipo, com ano de nascimento como campo adicional (não decisório) e validação opcional de idade mínima só para o tipo `Idoso`

Adicionar o campo de nascimento por seu próprio valor informativo, e usá-lo apenas como uma checagem de sanidade (ex: rejeitar alguém de 20 anos tentando se cadastrar como idoso), sem jamais decidir o tipo sozinho.

**Vantagens:**
- Preserva a escolha explícita como decisão principal, evitando os problemas da Alternativa 2.
- Adiciona valor informativo (idade exibível) e uma proteção contra erro grosseiro de cadastro, sem bloquear os casos legítimos descritos acima.

**Desvantagens:**
- Ainda não implementada — fica como evolução possível, não como decisão tomada agora.

## Justificativa

- **Compatibilidade com o projeto:** mantém a coerência com a modelagem original de `Usuario`/`Idoso`/`Familiar` (ADR-0002), que já trata os dois como papéis, não como categorias demográficas.
- **Correção de domínio:** evita confundir "papel no cuidado" com "idade cronológica", dois conceitos que podem divergir na vida real, mesmo que coincidam na maioria dos casos.

## Consequências

**Positivas**
- Nenhum cenário legítimo de uso é bloqueado por uma inferência automática de idade que nem sempre corresponde ao papel real da pessoa.
- Nenhuma mudança de código necessária — decisão confirma o comportamento já existente.

**Negativas**
- Sem nenhuma validação de consistência entre idade e tipo escolhido, um erro de cadastro (a pessoa escolher o tipo errado por engano) não é pego automaticamente pelo sistema.

## Impactos

Nenhum impacto de código — decisão de manter o comportamento já implementado desde a ADR-0002.

## Implementação

N/A — nenhuma mudança de código associada a esta ADR.

## Observações

A Alternativa 3 (ano de nascimento como campo informativo, com validação opcional de idade mínima para `Idoso`) fica registrada como evolução possível, caso o projeto queira adicionar essa proteção no futuro, sem reabrir a discussão sobre inferência automática de tipo.

## Data

13/09/2026

## Responsáveis

- Gilvan Pedro