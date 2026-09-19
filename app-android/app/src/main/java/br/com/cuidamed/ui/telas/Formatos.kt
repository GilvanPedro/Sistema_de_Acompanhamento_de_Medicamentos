package br.com.cuidamed.ui.telas

import br.com.cuidamed.data.MedicamentoDto
import br.com.cuidamed.data.rotuloDoDia
import br.com.cuidamed.data.rotuloDoTipo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

private val DIAS_ORDEM = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

/** "2026-09-19T12:05:32.59" -> "19/09 às 12:05". */
fun dataHoraCurta(iso: String): String = try {
    LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR))
} catch (e: Exception) {
    iso
}

fun horaDe(iso: String): String = try {
    LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm", PT_BR))
} catch (e: Exception) {
    iso
}

/** "sábado, 19 de setembro" */
fun dataDeHoje(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR))

fun primeiroNome(nome: String): String = nome.trim().substringBefore(' ')

fun detalheDoRemedio(m: MedicamentoDto) = "${rotuloDoTipo(m.tipo)}  ·  ${rotuloDoDia(m.diaSemana)}, ${m.horario}"

fun remediosEmOrdem(lista: List<MedicamentoDto>) =
    lista.sortedWith(compareBy({ DIAS_ORDEM.indexOf(it.diaSemana) }, { it.horario }))
