package br.com.cuidamed.data

// Gerado a partir do mesmo texto da página do servidor (politica-de-privacidade.html). Mudou o texto? Suba a versão nos dois.

const val VERSAO_DA_POLITICA = "1.0"
const val EMAIL_DE_PRIVACIDADE = "gilvanpedro2006@gmail.com"

val INTRODUCAO_DA_POLITICA = "Esta política explica, em linguagem simples, quais dados o CuidaMed guarda, para que usa, com quem divide e como você controla tudo isso, conforme a Lei Geral de Proteção de Dados (LGPD, Lei 13.709/2018)."

const val VIGENCIA_DA_POLITICA = "Versão 1.0 · em vigor desde 19 de setembro de 2026"

/** Cada seção: título e parágrafos. */
val SECOES_DA_POLITICA: List<Pair<String, List<String>>> = listOf(
    "1. Quem é o responsável" to listOf(
        "O CuidaMed é mantido por Gilvan Pedro, que responde pelos dados como controlador (LGPD, art. 5º, VI).",
        "Contato para qualquer pedido ou dúvida sobre seus dados: gilvanpedro2006@gmail.com.",
    ),
    "2. Quais dados guardamos" to listOf(
        "Dados da conta: nome, e-mail e senha. A senha é guardada só de forma criptografada (hash): nem nós conseguimos ler.",
        "Dados de saúde (dados pessoais sensíveis): o nome, o tipo, o dia da semana e o horário dos remédios cadastrados, e o registro de cada vez que o remédio foi marcado como tomado (com data e hora).",
        "Vínculos: quem é familiar de quem, e os pedidos de vínculo.",
        "Dados técnicos: o código do aparelho para enviar notificações (push) e as datas em que você aceitou esta política.",
        "No seu celular, o app guarda uma cópia dos seus dados, criptografada, para funcionar sem internet. Sair da conta apaga essa cópia.",
    ),
    "3. Para que usamos" to listOf(
        "Só para o app funcionar: lembrar os horários dos remédios, mostrar o histórico, avisar familiares quando um remédio é esquecido ou tomado e manter sua conta segura.",
        "Não vendemos seus dados e não os usamos para propaganda.",
        "O app mostra banners de anúncios, escolhidos ao acaso e iguais para todos: eles não usam os seus dados pessoais nem os seus remédios. Você pode fechar qualquer anúncio no botão \"Fechar\". Ao tocar num anúncio, você sai do CuidaMed e vai para o site do anunciante, que tem a política de privacidade própria dele.",
        "Para mostrar aos anunciantes como os banners se saem, o app conta, sem identificar ninguém, quantas vezes cada banner apareceu e foi tocado e se quem usa o app é idoso ou familiar. Essas contagens são somas por dia, sem nome, e-mail, endereço de IP nem identificador do aparelho.",
    ),
    "4. Por que podemos usar (base legal)" to listOf(
        "O seu consentimento (LGPD, art. 7º, I). Como os dados de remédios são dados de saúde, pedimos o consentimento de forma específica e destacada (art. 11, I): é o aceite que você dá ao criar a conta.",
        "Você pode retirar o consentimento quando quiser, excluindo a conta.",
    ),
    "5. Com quem os dados são compartilhados" to listOf(
        "Com os familiares vinculados: quando o idoso aceita o vínculo, o familiar passa a ver os remédios, os horários e o histórico dele. O idoso pode remover um familiar a qualquer momento.",
        "Com empresas que mantêm o serviço no ar (operadoras): Render (hospedagem da API), Neon (banco de dados) e Google Firebase (envio de notificações). O aviso enviado pelo Firebase só diz que há novidade: não leva nome, remédio nem horário.",
        "Esses serviços podem ter servidores fora do Brasil (transferência internacional, LGPD art. 33). Escolhemos provedores que adotam medidas de segurança e protegem os dados.",
        "Não compartilhamos seus dados com mais ninguém, a não ser por obrigação legal.",
    ),
    "6. Por quanto tempo guardamos" to listOf(
        "Enquanto a sua conta existir.",
        "Ao excluir a conta, apagamos os seus remédios, o histórico, os vínculos e os códigos de notificação, e o cadastro deixa de ter nome e e-mail. Cópias de segurança dos provedores podem levar algum tempo para desaparecer.",
    ),
    "7. Seus direitos" to listOf(
        "Você pode, a qualquer momento (LGPD, art. 18): confirmar que tratamos seus dados; acessar e receber uma cópia; corrigir; pedir a eliminação; saber com quem compartilhamos; e retirar o consentimento.",
        "No app, em \"Meus dados\": corrija nome e e-mail, baixe uma cópia de tudo com \"Baixar meus dados\" e exclua a conta com \"Excluir minha conta\".",
        "Se preferir, escreva para gilvanpedro2006@gmail.com. Você também pode reclamar à Autoridade Nacional de Proteção de Dados (ANPD).",
    ),
    "8. Como protegemos" to listOf(
        "A comunicação com o servidor usa HTTPS. As senhas são guardadas com hash. A cópia no celular é criptografada e fica fora do backup na nuvem do Android. O acesso do familiar depende da aceitação do idoso.",
        "Nenhum sistema é 100% seguro. Se houver um incidente que possa causar risco a você, avisaremos você e a ANPD, como a lei exige.",
    ),
    "9. Quem pode usar" to listOf(
        "O CuidaMed é destinado a maiores de 18 anos. Um familiar ou responsável pode ajudar o idoso a criar a conta, com o consentimento dele.",
    ),
    "10. Mudanças nesta política" to listOf(
        "Esta é a versão 1.0, em vigor desde 19 de setembro de 2026. Se o texto mudar, o app pedirá que você leia e aceite de novo.",
    ),
)
