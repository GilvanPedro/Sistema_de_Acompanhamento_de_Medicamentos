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

/**
 * Um remédio que se toma em vários dias da semana. No servidor cada dia é um registro (mesmo nome, tipo e horário); para a
 * pessoa é um remédio só, "Segunda, quarta e sexta".
 */
data class GrupoDeRemedio(val remedios: List<MedicamentoDto>) {
    private val primeiro get() = remedios.first()
    val nome get() = primeiro.nome
    val tipo get() = primeiro.tipo
    val horario get() = primeiro.horario
    val dias: Set<String> get() = remedios.map { it.diaSemana }.toSet()

    /** O registro de um dia da semana, ou null se o remédio não é tomado nele. */
    fun doDia(dia: String) = remedios.firstOrNull { it.diaSemana == dia }

    /** O registro em que o "já tomei" de hoje vale: o de hoje, ou o primeiro se hoje não é dia deste remédio. */
    fun paraTomar(hoje: String = LocalDate.now().dayOfWeek.name) = doDia(hoje) ?: primeiro
}

/** Junta os registros de mesmo nome, tipo e horário em um só remédio, ordenados pelo horário e pelo nome. */
fun agruparRemedios(lista: List<MedicamentoDto>): List<GrupoDeRemedio> =
    lista.groupBy { Triple(it.nome.trim().lowercase(PT_BR), it.tipo, it.horario) }.values
        .map { GrupoDeRemedio(it.sortedBy { m -> DIAS_ORDEM.indexOf(m.diaSemana) }) }
        .sortedWith(compareBy({ it.horario }, { it.nome.lowercase(PT_BR) }))

/** O grupo a que um registro pertence. */
fun grupoDoRemedio(lista: List<MedicamentoDto>, remedioId: Int): GrupoDeRemedio? =
    agruparRemedios(lista).firstOrNull { g -> g.remedios.any { it.id == remedioId } }

/** "Todos os dias", "Segunda a sexta", "Segunda, quarta e sexta". */
fun rotuloDosDias(dias: Set<String>): String {
    val ordenados = DIAS_ORDEM.filter { it in dias }
    return when {
        ordenados.size == 7 -> "Todos os dias"
        ordenados.size == 1 -> rotuloDoDia(ordenados[0])
        ordenados == DIAS_ORDEM.take(5) -> "Segunda a sexta"
        ordenados == DIAS_ORDEM.takeLast(2) -> "Sábado e domingo"
        else -> ordenados.mapIndexed { i, dia -> rotuloDoDia(dia).let { if (i == 0) it else it.replaceFirstChar { c -> c.lowercase() } } }
            .let { it.dropLast(1).joinToString(", ") + " e " + it.last() }
    }
}

fun detalheDoGrupo(g: GrupoDeRemedio) = "${rotuloDoTipo(g.tipo)}  ·  ${rotuloDosDias(g.dias)}, ${g.horario}"
