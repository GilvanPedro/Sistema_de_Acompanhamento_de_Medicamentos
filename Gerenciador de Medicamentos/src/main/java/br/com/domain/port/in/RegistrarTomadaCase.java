package br.com.domain.port.in;

import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;

public interface RegistrarTomadaCase {
    HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou);

    /** Com a hora em que a tomada aconteceu de verdade (null = agora). */
    HistoricoMedicamento registrarTomada(Idoso idoso, Medicamento medicamento, boolean tomou, java.time.LocalDateTime quando);
}
