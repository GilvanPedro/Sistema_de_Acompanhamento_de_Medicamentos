package br.com.domain.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;

public class Medicamento {
    private int id;
    private int idosoId;
    private String nome;
    private LocalTime horarioMedicamento;
    private DayOfWeek diaSemana;
    private TipoMedicamento tipoMedicamento;
    /** Desde quando o dia e o horário atuais valem (só há "não tomou" a partir daí); null = desconhecido, sem faltas. */
    private LocalDateTime vigenteDesde;

    public Medicamento(int id, int idosoId, String nome, LocalTime horarioMedicamento, DayOfWeek diaSemana, TipoMedicamento tipoMedicamento) {
        this.id = id;
        this.idosoId = idosoId;
        this.nome = nome;
        this.horarioMedicamento = horarioMedicamento;
        this.diaSemana = diaSemana;
        this.tipoMedicamento = tipoMedicamento;
    }

    public Medicamento(int id, int idosoId, String nome, LocalTime horarioMedicamento, DayOfWeek diaSemana, TipoMedicamento tipoMedicamento,
                       LocalDateTime vigenteDesde) {
        this(id, idosoId, nome, horarioMedicamento, diaSemana, tipoMedicamento);
        this.vigenteDesde = vigenteDesde;
    }

    public int getId() { return id; }
    public int getIdosoId() { return idosoId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public LocalTime getHorarioMedicamento() { return horarioMedicamento; }
    public void setHorarioMedicamento(LocalTime horarioMedicamento) { this.horarioMedicamento = horarioMedicamento; }
    public DayOfWeek getDiaSemana() { return diaSemana; }
    public void setDiaSemana(DayOfWeek diaSemana) { this.diaSemana = diaSemana; }
    public TipoMedicamento getTipoMedicamento() { return tipoMedicamento; }
    public void setTipoMedicamento(TipoMedicamento tipoMedicamento) { this.tipoMedicamento = tipoMedicamento; }
    public LocalDateTime getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDateTime vigenteDesde) { this.vigenteDesde = vigenteDesde; }

    @Override
    public String toString() {
        return String.format("[%d] %s | %s | %s às %s | Idoso id %d",
                id, nome, tipoMedicamento.getDescricao(), diaSemana, horarioMedicamento, idosoId);
    }
}