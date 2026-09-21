# ADR-0056 — Rodízio justo dos banners

## Status

Aceito

## Contexto

O app sorteava um banner ao acaso a cada tela (ADR-0054). Sorteio simples só iguala as exibições "na média": com poucas exibições por dia a diferença entre banners fica grande, e um **banner novo começa atrás** dos antigos e assim permanece. Com a lista de patrocinadores crescendo, isso significa anunciantes recebendo quantidades bem diferentes de exibições pelo mesmo espaço, e é justamente o número que eles vão cobrar e conferir (ADR-0055).

## Decisão

### 1. O servidor calcula um peso para cada banner

`GET /api/v1/anuncios` (público, sem dado de ninguém) devolve a lista de banners no mesmo formato do `anuncios.json`, mais o campo **`peso`** e as imagens já com endereço completo. O peso é calculado por `PesosDosBanners` a partir das exibições dos **últimos 14 dias** (as contagens do ADR-0055), no máximo uma vez por minuto:

- Cada banner tem uma **parte desejada** das exibições. Por padrão é igual para todos (`peso` 1 no catálogo).
- O **ajuste** compara as exibições recentes do banner com a sua parte: `ajuste = ((alvo + 30) / (feitas + 30)) ^ 0,5`, limitado entre 0,25 e 4. Quem está abaixo da sua parte ganha peso; quem está acima é freado.
- O **peso final** é a parte desejada vezes o ajuste. Se todos estão na sua parte, todos têm peso igual.
- Só os últimos 14 dias contam, para um banner novo alcançar os outros **sem ser inundado durante meses**; o limite de 4 vezes evita que ele domine as telas enquanto alcança.

### 2. O app sorteia com esses pesos

O app baixa a lista com pesos, sorteia com chance proporcional ao peso e **renova a lista a cada 15 minutos** (para o rodízio acompanhar o servidor). Plano B: se essa lista falhar, usa o arquivo estático `anuncios.json` (sorteio simples, o mesmo que as versões antigas do app sempre usaram) e, por último, a cópia guardada no aparelho. As versões antigas do app continuam funcionando, sem rodízio.

### 3. Campo opcional `peso` no catálogo

> Atualização: hoje o peso é um campo do cadastro do banner no painel (ADR-0057); o texto abaixo descreve o desenho original com o arquivo `anuncios.json`.

Em cada entrada do `anuncios.json`, `"peso"` define a parte desejada relativa (padrão 1): `2` recebe o dobro da parte dos outros; `0` **pausa** o banner sem apagá-lo. Não é preciso mexer nele para manter todos iguais. Se todos os pesos forem 0, todos valem igual (em vez de não mostrar nada).

## Como validamos (simulação de 60 dias)

6 banners, e 4 novos entram no dia 30; 20 sorteios de semente diferente por situação. Diferença média entre o banner mais distante e a média das exibições dos últimos 14 dias (quanto menor, mais parelho):

| Situação | Sorteio simples | Rodízio justo |
|---|---|---|
| 10 dias depois de entrarem 4 banners novos, 60 exibições por dia | 31,7% | 16,1% |
| idem, 300 por dia | 24,8% | 7,0% |
| idem, 3.000 por dia | 22,9% | 3,1% |
| Dia a dia (dia 59), 60 por dia | 20,2% | 17,3% |
| idem, 300 por dia | 9,0% | 6,8% |
| idem, 3.000 por dia | 2,8% | 2,4% |

O ganho grande está em **alcançar os banners novos**. No dia a dia com poucas exibições por dia, o ganho é pequeno: há um ruído natural (cada exibição é um sorteio) que nenhum ajuste elimina. Uma primeira versão, com correção mais forte (expoente 1), ficava **pior** que o sorteio simples com muitas exibições por exagerar e oscilar; por isso o expoente é 0,5. Os testes automáticos guardam esses dois resultados.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Manter o sorteio simples | Deixa banners novos atrás e diferenças grandes com poucas exibições |
| Sempre mostrar o banner com menos exibições | Todos os aparelhos veriam o mesmo banner ao mesmo tempo até ele alcançar os outros; ruim para o usuário e para a leitura dos números |
| Sortear no app com base nas contagens do próprio aparelho | Cada aparelho só conhece a si mesmo: não iguala o total |
| Alcançar em todo o histórico (não só 14 dias) | Um banner novo seria inundado por meses |

## Consequências

- **O total de exibições é finito.** Ao acrescentar banners, as exibições se dividem entre mais gente: cada banner recebe **menos exibições em números absolutos**, e o que o rodízio mantém é a **igualdade entre eles**. Para manter a quantidade de cada patrocinador quando a lista cresce, é preciso mais usuários (ou menos banners).
- A igualdade é aproximada, não exata: o ruído do sorteio permanece, principalmente com poucas exibições.
- O painel mostra a **parte das exibições de cada banner** no mês, para conferir o equilíbrio.
- A lista com pesos é calculada no servidor, então precisa do banco (contagens do ADR-0055). Sem contagens, todos têm peso igual.
- Versões antigas do app e o arquivo estático continuam existindo e funcionando.
