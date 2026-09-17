package br.com.domain.model;

public class NotificacaoMedicamento {
    private final Medicamento medicamento;
    private final TipoNotificacao tipo;

    public NotificacaoMedicamento(Medicamento medicamento, TipoNotificacao tipo) {
        this.medicamento = medicamento;
        this.tipo = tipo;
    }

    public Medicamento getMedicamento() { return medicamento; }
    public TipoNotificacao getTipo() { return tipo; }
}