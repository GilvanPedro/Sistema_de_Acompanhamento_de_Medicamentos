package br.com.model;

public enum TipoMedicamento {
    COMPRIMIDO("Medicamento em Comprimido"),
    GOTAS("Medicamento em Gotas"),
    INJECAO("Medicamento de Injeito"),
    OUTRO("Outro Tipo de Medicamento");

    private String descricao;

    TipoMedicamento(String descricao){
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
