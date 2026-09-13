package br.com.adapter.in.console;

import br.com.adapter.out.persistence.MedicamentoCsvAdapter;
import br.com.domain.model.Medicamento;

public class Teste2 {
    public static void main(String[] args) {
        MedicamentoCsvAdapter medicamentoCsvAdapter = new MedicamentoCsvAdapter();

        Medicamento medicamento = medicamentoCsvAdapter.buscarPorId(6).get(0);

        System.out.println(medicamento);
    }
}
