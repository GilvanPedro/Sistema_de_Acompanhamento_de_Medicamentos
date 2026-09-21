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
}
