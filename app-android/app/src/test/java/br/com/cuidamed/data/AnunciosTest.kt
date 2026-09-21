package br.com.cuidamed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnunciosTest {

    private val catalogo = "https://servidor.exemplo/anuncios/anuncios.json"

    @Test
    fun imagemRelativaVemDaPastaDoCatalogoEAbsolutaHttpsValeComoEsta() {
        assertEquals("https://servidor.exemplo/anuncios/b1.png", enderecoDaImagem(catalogo, "b1.png"))
        assertEquals("https://servidor.exemplo/outra/b2.png", enderecoDaImagem(catalogo, "../outra/b2.png"))
        assertEquals("https://cdn.exemplo/x/b3.png", enderecoDaImagem(catalogo, "https://cdn.exemplo/x/b3.png"))
        assertEquals("https://servidor.exemplo/anuncios/com%20espaco.png", enderecoDaImagem(catalogo, " com%20espaco.png "))
    }

    @Test
    fun imagemForaDeHttpsOuInvalidaEhRecusada() {
        assertNull(enderecoDaImagem(catalogo, "http://cdn.exemplo/b.png"))
        assertNull(enderecoDaImagem(catalogo, "file:///sdcard/b.png"))
        assertNull(enderecoDaImagem(catalogo, "javascript:alert(1)"))
        assertNull(enderecoDaImagem(catalogo, "não é um endereço"))
    }

    @Test
    fun soLinksHttpsSaoAbertos() {
        assertEquals("https://loja.exemplo/promo", linkSeguro(" https://loja.exemplo/promo "))
        assertNull(linkSeguro(null))
        assertNull(linkSeguro("http://loja.exemplo"))
        assertNull(linkSeguro("tel:+5511999999999"))
        assertNull(linkSeguro("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(linkSeguro("https://"))
    }

    @Test
    fun linkDeEmailSoValeComDestinatarioAssuntoEMensagem() {
        assertEquals("mailto:pessoa@exemplo.com", linkSeguro("mailto:pessoa@exemplo.com"))
        assertEquals(
            "mailto:pessoa@exemplo.com?subject=Quero%20anunciar",
            linkSeguro(" mailto:pessoa@exemplo.com?subject=Quero%20anunciar "),
        )
        assertEquals(
            "mailto:a.b+c@exemplo.com.br?subject=Oi&body=Ola%20tudo%20bem",
            linkSeguro("mailto:a.b+c@exemplo.com.br?subject=Oi&body=Ola%20tudo%20bem"),
        )
        assertNull(linkSeguro("mailto:"))
        assertNull(linkSeguro("mailto:sem-arroba"))
        assertNull(linkSeguro("mailto:pessoa@exemplo.com?bcc=outro@exemplo.com"))
        assertNull(linkSeguro("mailto:pessoa@exemplo.com?attach=/sdcard/segredo.txt"))
        assertNull(linkSeguro("mailto:pessoa@exemplo.com?subject=a&cc=b@c.com"))
        assertNull(linkSeguro("mailto:pessoa@exemplo.com,outro@exemplo.com"))
    }

    @Test
    fun catalogoEhLidoIgnorandoCamposDesconhecidosEInvalidoViraListaVazia() {
        val json = """{"versao": 3, "anuncios": [
            {"id": "a", "imagem": "a.png", "link": "https://x.exemplo", "texto": "Anúncio A", "novo": true},
            {"id": "b", "imagem": "b.png"}]}"""
        val lista = lerCatalogo(json)
        assertEquals(listOf("a", "b"), lista.map { it.id })
        assertEquals("Anúncio A", lista[0].texto)
        assertNull(lista[1].link)
        assertEquals("Anúncio", lista[1].texto)

        assertTrue(lerCatalogo("isto não é json").isEmpty())
        assertTrue(lerCatalogo("""{"anuncios": "quebrado"}""").isEmpty())
        assertTrue(lerCatalogo("""{}""").isEmpty())
    }

    @Test
    fun escolheUmDoConjuntoAoAcasoEListaVaziaNaoTemBanner() {
        val lista = (1..5).map { Anuncio("id$it", "$it.png") }
        val sorteados = (1..200).map { lista.escolher(Random(it))!!.id }.toSet()
        assertTrue("todos devem poder sair", sorteados.size == 5)
        assertNull(emptyList<Anuncio>().escolher())
        // o mesmo sorteio dá o mesmo resultado (é o gerador que decide, não o estado do teste)
        assertEquals(lista.escolher(Random(7)), lista.escolher(Random(7)))
    }

    @Test
    fun sorteioSegueOsPesosEPesoZeroOuInvalidoTiraODoSorteio() {
        val lista = listOf(
            Anuncio("a", "a.png", peso = 1.0),
            Anuncio("b", "b.png", peso = 3.0),
            Anuncio("pausado", "p.png", peso = 0.0),
            Anuncio("negativo", "n.png", peso = -2.0),
            Anuncio("invalido", "i.png", peso = Double.NaN),
        )
        val sorteio = Random(123)
        val contagem = (1..20_000).groupingBy { lista.escolher(sorteio)!!.id }.eachCount()
        assertEquals(setOf("a", "b"), contagem.keys) // os pausados/inválidos nunca saem
        val parteDeB = contagem.getValue("b") / 20_000.0
        assertTrue("b tem 3 de 4 partes (75%), deu $parteDeB", parteDeB in 0.73..0.77)
    }

    @Test
    fun semNenhumPesoValidoTodosValemIgualEmVezDeNaoMostrarNada() {
        val lista = listOf(Anuncio("a", "a.png", peso = 0.0), Anuncio("b", "b.png", peso = 0.0))
        val sorteados = (1..200).map { lista.escolher(Random(it))!!.id }.toSet()
        assertEquals(setOf("a", "b"), sorteados)
    }

    @Test
    fun aListaDoServidorJaVemComPesoEAEstaticaAssumePesoUm() {
        val comPeso = lerCatalogo("""{"anuncios":[{"id":"a","imagem":"https://cdn.exemplo/a.png","peso":2.5}]}""")
        assertEquals(2.5, comPeso[0].peso, 1e-9)
        val estatica = lerCatalogo("""{"anuncios":[{"id":"a","imagem":"a.png"}]}""")
        assertEquals(1.0, estatica[0].peso, 1e-9)
    }

    @Test
    fun normalizarPoeEnderecoCompletoNaImagemETiraAsInvalidas() {
        val lista = listOf(
            Anuncio("rel", "b1.png"),
            Anuncio("abs", "https://cdn.exemplo/b2.png"),
            Anuncio("http", "http://cdn.exemplo/b3.png"),
            Anuncio("lixo", "isto nao e endereco"),
        )
        val ok = normalizar(catalogo, lista)
        assertEquals(listOf("rel", "abs"), ok.map { it.id })
        assertEquals("https://servidor.exemplo/anuncios/b1.png", ok[0].imagem)
        assertEquals("https://cdn.exemplo/b2.png", ok[1].imagem)
    }
}
