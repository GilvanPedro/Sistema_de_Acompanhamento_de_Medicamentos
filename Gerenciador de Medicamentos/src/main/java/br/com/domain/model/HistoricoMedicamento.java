package br.com.domain.model;

import java.time.LocalDateTime;

public class HistoricoMedicamento {
    private int id;
    private Medicamento medicamento;
    private Idoso idoso;
    private LocalDateTime dataHoraTomada;
    private boolean foiTomado;

    public HistoricoMedicamento(int id, Medicamento medicamento, Idoso idoso, LocalDateTime dataHoraTomada, boolean foiTomado) {
        this.id = id;
        this.medicamento = medicamento;
        this.idoso = idoso;
        this.dataHoraTomada = dataHoraTomada;
        this.foiTomado = foiTomado;
    }

    public int getId() {
        return id;
    }

    public Medicamento getMedicamento() {
        return medicamento;
    }

    public Idoso getIdoso() {
        return idoso;
    }

    public LocalDateTime getDataHoraTomada() {
        return dataHoraTomada;
    }

    public boolean isFoiTomado() {
        return foiTomado;
    }

    @Override
    public String toString() {
        return "Medicamento: " + medicamento.getNome() +
                ", Paciente: " + idoso.getNome() +
                ", Data/Hora: " + dataHoraTomada.toString() +
                ", Tomou: " + (foiTomado ? "Sim" : "Não");
    }
}
