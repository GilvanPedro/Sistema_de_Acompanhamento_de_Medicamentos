package br.com.api;

import br.com.model.Familiar;
import br.com.model.Idoso;
import br.com.model.Medicamento;

public class NotificacoesApi {
    public void notificarTomouRemedio(Idoso idoso, Medicamento medicamento){
        for(Familiar familiar : idoso.getFamiliares()){
            System.out.println(
                    "Aviso para " + familiar.getNome() +
                            ": O paciente " + idoso.getNome() +
                            " tomou " + medicamento.getNome());
        }
    }

    public void notificarEsqueceuRemedio(Idoso idoso, Medicamento medicamento){
        for(Familiar familiar : idoso.getFamiliares()){
            System.out.println(
                    "Aviso para " + familiar.getNome() +
                            ": O paciente " + idoso.getNome() +
                            " não tomou o medicamento " + medicamento.getNome());
        }
    }

    public void lembrarTomarRemedio(Idoso idoso, Medicamento medicamento){
        System.out.println("Aviso para " + idoso.getNome() + ": lembre-se de tomar o remédio: " + medicamento.getNome());

    }
}
