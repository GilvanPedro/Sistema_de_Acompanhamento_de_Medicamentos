package br.com.model;

import java.time.LocalTime;
import java.time.DayOfWeek;

public class Medicamento {
    private String nome;
    private LocalTime horarioMedicamento;
    private DayOfWeek diaSemana;
    private  TipoMedicamento tipoMedicamento;

    public Medicamento(String nome, LocalTime horarioMedicamento, DayOfWeek diaSemana, TipoMedicamento tipoMedicamento) {
        this.nome = nome;
        this.horarioMedicamento = horarioMedicamento;
        this.diaSemana = diaSemana;
        this.tipoMedicamento = tipoMedicamento;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public LocalTime getHorarioMedicamento() {
        return horarioMedicamento;
    }

    public void setHorarioMedicamento(LocalTime horarioMedicamento) {
        this.horarioMedicamento = horarioMedicamento;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    public TipoMedicamento getTipoMedicamento() {
        return tipoMedicamento;
    }

    public void setTipoMedicamento(TipoMedicamento tipoMedicamento) {
        this.tipoMedicamento = tipoMedicamento;
    }

    @Override
    public String toString() {
        return "Medicamento: " + nome +
                ", Tipo de Medicamento: " + tipoMedicamento.getDescricao() +
                ", Horario Tomado: " + horarioMedicamento.toString();
    }
}
