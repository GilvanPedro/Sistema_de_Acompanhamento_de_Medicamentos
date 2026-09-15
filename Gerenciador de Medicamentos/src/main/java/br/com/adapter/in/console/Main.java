package br.com.adapter.in.console;

import br.com.config.AppConfig;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.SalvarUsuarioPort;

import java.util.List;
import java.util.Objects;

public class Main {

    public static void main(String[] args) {
        SalvarUsuarioPort usuarioPort = AppConfig.getUsuarioPort();

        Idoso idoso = usuarioPort.listarTodos().stream()
                .filter(Idoso.class::isInstance)
                .map(Idoso.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum idoso cadastrado."));

        List<Familiar> familiares = usuarioPort.listarTodos().stream()
                .filter(Familiar.class::isInstance)
                .map(Familiar.class::cast)
                .toList();

        if (familiares.isEmpty()) {
            throw new IllegalStateException("Nenhum familiar cadastrado.");
        }

        System.out.println("=== Adicionando vínculos ===");
        for (Familiar familiar : familiares) {
            boolean jaVinculado = idoso.getFamiliares().stream()
                    .anyMatch(vinculado -> vinculado.getId() == familiar.getId()
                            && Objects.equals(vinculado.getNome(), familiar.getNome()));

            if (!jaVinculado) {
                usuarioPort.salvarVinculo(idoso.getId(), familiar.getId());
                System.out.printf("Vínculo adicionado: idoso %d -> familiar %d (%s)%n",
                        idoso.getId(), familiar.getId(), familiar.getNome());
            } else {
                System.out.printf("Vínculo já existente: idoso %d -> familiar %d (%s)%n",
                        idoso.getId(), familiar.getId(), familiar.getNome());
            }
        }

        System.out.println("\n=== Buscando vínculos do idoso ===");
        Usuario usuarioEncontrado = usuarioPort.buscarPorId(idoso.getId());
        if (!(usuarioEncontrado instanceof Idoso idosoEncontrado)) {
            throw new IllegalStateException("O usuário encontrado não é um idoso.");
        }

        System.out.printf("Idoso: %d - %s%n", idosoEncontrado.getId(), idosoEncontrado.getNome());
        List<Familiar> vinculos = idosoEncontrado.getFamiliares();
        System.out.println("Familiares vinculados: " + vinculos.size());
        for (Familiar familiar : vinculos) {
            System.out.printf("- %d - %s (%s)%n",
                    familiar.getId(), familiar.getNome(), familiar.getEmail());
        }
    }
}
