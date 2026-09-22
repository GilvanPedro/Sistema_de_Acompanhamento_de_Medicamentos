# ADR-0064 — Exclusão de conta por um link público

## Status

Aceito

## Contexto

Excluir a conta já existia, mas só dentro do app, com a senha atual (ADR-0044/LGPD). Quem esqueceu a senha, perdeu o acesso à conta, ou prefere não instalar o app, não tinha como excluir os próprios dados (LGPD, art. 18, VI). A Google Play também exige, para apps que permitem criar conta, um caminho de exclusão que funcione **sem precisar abrir o app** (Data safety / Account deletion).

## Decisão

Uma página pública, **`/excluir-conta`**, com o mesmo desenho de segurança do "esqueci minha senha" (ADR-0063):

1. A pessoa digita o **e-mail** da conta.
2. A resposta é **sempre a mesma**, exista a conta ou não (não revela quais e-mails estão cadastrados).
3. Se existir conta, chega um **e-mail com um link de confirmação**: token de 256 bits, só o hash fica no banco (migração **V13**, tabela `exclusao_conta` — separada da `redefinicao_senha` da ADR-0063, para um link vazado de uma finalidade nunca servir para a outra), válido por **30 minutos**, uso único.
4. Abrir o link mostra **o nome e o e-mail de quem seria excluído**, e uma lista clara do que se perde, **antes** de apagar qualquer coisa. Só apaga depois de um clique explícito ("Sim, excluir minha conta e meus dados").
5. A exclusão em si reaproveita o mesmo caminho da exclusão de dentro do app: `ExcluirUsuarioService` (apaga remédios e histórico do idoso, remove vínculos, escreve por cima do nome e do e-mail), mais o encerramento de todas as sessões, a remoção dos aparelhos cadastrados para push e dos consentimentos, e agora também de qualquer **pedido de nova senha pendente** daquela conta (para um link antigo de "esqueci a senha" não sobreviver a uma conta que já foi apagada).
6. Depois de excluir, um último e-mail confirma que foi feito.
7. Limites (iguais em espírito aos da ADR-0063): 3 pedidos por hora por e-mail, 5 por hora por IP, 10 confirmações por hora por IP.

A exclusão de dentro do app (com senha) também passou a apagar pedidos pendentes desta página, e vice-versa: as duas pontas ficam consistentes.

### Página compartilhada

O visual das duas páginas públicas (nova senha e exclusão de conta) foi extraído para `PaginaPublica` (`adapter/in/web/paginas`), para não duplicar o CSS e manter as duas com a mesma cara.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Só documentar que a exclusão existe dentro do app | Não atende quem perdeu a senha, nem a exigência da Play de um caminho que não dependa do app |
| Excluir na hora, só com o e-mail, sem confirmar por link | Qualquer pessoa que soubesse o e-mail de outra apagaria a conta dela |
| Usar o mesmo token/tabela do "esqueci minha senha" | Um link vazado (por exemplo, no histórico do navegador de um computador compartilhado) serviria para as duas coisas; tabelas separadas limitam o estrago de cada um |

## Consequências

- **A política de privacidade foi atualizada** (seção 7) para citar esse caminho, mas **sem subir de versão**: é a documentação de um novo acesso a um direito que já existia, não uma mudança em quais dados são tratados ou para quê.
- É preciso rodar `V13__criar_exclusao_conta.sql` na Neon antes de publicar.
- Depende do envio de e-mail (ADR-0063, Brevo) já estar configurado; sem ele, o pedido responde normalmente mas ninguém recebe o link.
- O link de exclusão de uma conta some ao ela ser excluída; não é reaproveitável nem depois.

## Como ligar

1. Rodar `V13__criar_exclusao_conta.sql` na Neon (depois da V12).
2. Publicar o servidor. A página fica em `/excluir-conta` (link a divulgar na ficha do Play Console, em "Exclusão de conta").
