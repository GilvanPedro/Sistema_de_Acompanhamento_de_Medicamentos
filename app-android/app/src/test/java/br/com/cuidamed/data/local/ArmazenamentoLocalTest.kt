package br.com.cuidamed.data.local

import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.UsuarioDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArmazenamentoLocalTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val tudo = DadosLocais(
        usuario = UsuarioDto(3, "IDOSO", "Dona Maria", "maria@teste.com"),
        remedios = mapOf(3 to listOf(MedicamentoDto(1, 3, "Losartana", "08:00", "MONDAY", "COMPRIMIDO"))),
        pendencias = listOf(
            CriarRemedio("a", 3, -1, "Atenolol", "FRIDAY", "20:00", "GOTAS", "t"),
            EditarRemedio("b", 3, 1, "Losartana", nome = "Losartana 50mg", criadaEm = "t"),
            ExcluirRemedio("c", 3, 2, "Metformina", "t"),
            RegistrarTomada("d", 3, 1, "Losartana", "2026-09-19T08:05:00", "t"),
        ),
        conflitos = listOf("Algo não foi aplicado"),
        proximoIdLocal = -2,
    )

    @Test
    fun oQueFoiGravadoVoltaIgualEmOutraInstancia() = runBlocking {
        val dir = pasta.newFolder()
        ArmazenamentoLocal(dir, SemCifra).alterar(3) { tudo to Unit }
        val lido = ArmazenamentoLocal(dir, SemCifra).ler(3) // nova instância: lê do arquivo
        assertEquals(tudo, lido)
    }

    @Test
    fun cadaPessoaTemOSeuArquivo() = runBlocking {
        val armazenamento = ArmazenamentoLocal(pasta.newFolder(), SemCifra)
        armazenamento.alterar(3) { tudo to Unit }
        assertTrue(armazenamento.ler(4).pendencias.isEmpty())
    }

    @Test
    fun arquivoDanificadoOuComChavePerdidaViraVazioEmVezDeQuebrar() = runBlocking {
        val dir = pasta.newFolder()
        File(dir, "dados-3.bin").writeText("isto não é um dado válido")
        assertEquals(DadosLocais(), ArmazenamentoLocal(dir, SemCifra).ler(3))
    }

    @Test
    fun oConteudoGravadoPassaPelaCifra() = runBlocking {
        val dir = pasta.newFolder()
        val revertida = object : Cifra {
            override fun cifrar(texto: String) = texto.reversed()
            override fun decifrar(texto: String) = texto.reversed()
        }
        ArmazenamentoLocal(dir, revertida).alterar(3) { tudo to Unit }
        val bruto = File(dir, "dados-3.bin").readText()
        assertFalse("o nome do remédio não pode estar em texto puro", bruto.contains("Losartana"))
        assertEquals(tudo, ArmazenamentoLocal(dir, revertida).ler(3))
    }

    @Test
    fun apagarRemoveOArquivo() = runBlocking {
        val dir = pasta.newFolder()
        val armazenamento = ArmazenamentoLocal(dir, SemCifra)
        armazenamento.alterar(3) { tudo to Unit }
        armazenamento.apagar(3)
        assertFalse(File(dir, "dados-3.bin").exists())
        assertTrue(armazenamento.ler(3).pendencias.isEmpty())
    }
}
