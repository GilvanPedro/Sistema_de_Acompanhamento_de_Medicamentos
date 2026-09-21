package br.com.cuidamed.ui.telas

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.cuidamed.data.Resultado
import br.com.cuidamed.ui.LocalRepositorio
import br.com.cuidamed.ui.componentes.AvisoDaTela
import br.com.cuidamed.ui.componentes.BotaoGrande
import br.com.cuidamed.ui.componentes.Cartao
import br.com.cuidamed.ui.componentes.EstiloDoBotao
import br.com.cuidamed.ui.componentes.Mensagem
import br.com.cuidamed.ui.componentes.Secao
import br.com.cuidamed.ui.componentes.TextoSuave
import br.com.cuidamed.ui.componentes.Tom
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** O servidor recusa períodos de um ano ou mais; o app avisa antes de pedir. */
private const val MAXIMO_DE_DIAS = 365L

private fun paraData(milissegundos: Long): LocalDate = Instant.ofEpochMilli(milissegundos).atZone(ZoneOffset.UTC).toLocalDate()

private fun dataCurta(d: LocalDate) = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

/**
 * "Baixar em PDF": a pessoa escolhe o período (atalhos ou datas no calendário), o app baixa o PDF do servidor e oferece
 * abrir ou compartilhar (WhatsApp, e-mail, imprimir), para levar ao médico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaixarHistoricoEmPdf(idosoId: Int, nomeDoIdoso: String) {
    val repo = LocalRepositorio.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var escolhendo by remember { mutableStateOf(false) }
    var baixando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    var pronto by remember { mutableStateOf<Pair<File, String>?>(null) }

    fun baixar(de: LocalDate, ate: LocalDate) {
        if (ChronoUnit.DAYS.between(de, ate) >= MAXIMO_DE_DIAS) {
            mensagem = Mensagem("Escolha um período de até um ano.", Tom.AVISO)
            return
        }
        baixando = true
        mensagem = null
        pronto = null
        escopo.launch {
            val destino = arquivoDoPdf(contexto, de, ate)
            when (val r = repo.baixarHistoricoEmPdf(idosoId, de, ate, destino)) {
                is Resultado.Ok -> pronto = r.valor to "de ${dataCurta(de)} a ${dataCurta(ate)}"
                is Resultado.Falha -> mensagem = Mensagem(
                    if (r.codigo == null) "Para baixar o PDF é preciso estar com internet. Tente de novo quando conectar." else r.mensagem,
                    Tom.ERRO,
                )
            }
            baixando = false
        }
    }

    Secao("Levar ao médico")
    AvisoDaTela(mensagem)
    BotaoGrande("Baixar histórico em PDF", { escolhendo = true }, estilo = EstiloDoBotao.SECUNDARIO, carregando = baixando)
    pronto?.let { (arquivo, periodo) ->
        Cartao(Tom.OK) {
            Text("PDF pronto ($periodo)", style = MaterialTheme.typography.titleMedium)
            BotaoGrande("Abrir", { abrirPdf(contexto, arquivo) })
            BotaoGrande("Compartilhar ou imprimir", { compartilharPdf(contexto, arquivo, nomeDoIdoso) }, estilo = EstiloDoBotao.SECUNDARIO)
        }
    }

    if (escolhendo) {
        SeletorDePeriodo(
            aoCancelar = { escolhendo = false },
            aoEscolher = { de, ate ->
                escolhendo = false
                baixar(de, ate)
            },
        )
    }
}

/** Atalhos comuns e, logo abaixo, o calendário para escolher o primeiro e o último dia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDePeriodo(aoCancelar: () -> Unit, aoEscolher: (LocalDate, LocalDate) -> Unit) {
    val hoje = LocalDate.now()
    val estado = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !paraData(utcTimeMillis).isAfter(hoje)
            override fun isSelectableYear(year: Int) = year <= hoje.year
        },
    )
    val de = estado.selectedStartDateMillis?.let(::paraData)
    val ate = estado.selectedEndDateMillis?.let(::paraData) ?: de
    DatePickerDialog(
        onDismissRequest = aoCancelar,
        confirmButton = {
            TextButton(onClick = { if (de != null && ate != null) aoEscolher(de, ate) }, enabled = de != null && ate != null) {
                Text("Baixar", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", style = MaterialTheme.typography.titleMedium) } },
    ) {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextoSuave("Toque no primeiro e no último dia do período. Para um dia só, toque duas vezes no mesmo dia.")
            TextButton(onClick = { aoEscolher(hoje.minusDays(29), hoje) }) { Text("Últimos 30 dias") }
            TextButton(onClick = { aoEscolher(hoje.minusDays(89), hoje) }) { Text("Últimos 3 meses") }
        }
        DateRangePicker(estado, modifier = Modifier.weight(1f, fill = false), showModeToggle = false)
    }
}

/** Os PDFs ficam no cache do app; só o mais recente é guardado (são dados de saúde). */
private fun arquivoDoPdf(contexto: Context, de: LocalDate, ate: LocalDate): File {
    val pasta = File(contexto.cacheDir, "historico_pdf").apply { mkdirs() }
    pasta.listFiles()?.forEach { it.delete() }
    return File(pasta, "historico-$de-a-$ate.pdf")
}

private fun uriDo(contexto: Context, arquivo: File) =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)

private fun abrirPdf(contexto: Context, arquivo: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriDo(contexto, arquivo), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        contexto.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // sem leitor de PDF instalado: oferece compartilhar, que sempre tem para onde ir (Drive, e-mail, WhatsApp)
        compartilharPdf(contexto, arquivo, "")
    }
}

private fun compartilharPdf(contexto: Context, arquivo: File, nomeDoIdoso: String) {
    val envio = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uriDo(contexto, arquivo))
        .putExtra(Intent.EXTRA_SUBJECT, if (nomeDoIdoso.isBlank()) "Histórico de medicamentos" else "Histórico de medicamentos - $nomeDoIdoso")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(envio, "Enviar histórico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
