package br.com.domain.port.out;

import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

public interface NotificarPort {
    void lembrarIdoso(Idoso idoso, Medicamento medicamento);
    void avisarRemedioTomado(Idoso idoso, Medicamento medicamento);
    void avisarRemedioEsquecido(Idoso idoso, Medicamento medicamento);
}