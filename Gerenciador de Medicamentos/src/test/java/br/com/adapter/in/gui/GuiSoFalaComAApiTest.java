package br.com.adapter.in.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A interface gráfica é só um cliente da API: não pode importar serviços, portas, persistência nem o modelo de domínio
 * (a única exceção são os enums TipoMedicamento e TipoNotificacao, que são só listas de valores).
 */
class GuiSoFalaComAApiTest {

    private static final List<String> PERMITIDOS = List.of(
            "br.com.domain.model.TipoMedicamento", "br.com.domain.model.TipoNotificacao", "br.com.config.Ambiente");

    @Test
    void nenhumaTelaTocaNoBancoNemNasRegrasDeNegocio() throws IOException {
        Path pasta = Path.of("src/main/java/br/com/adapter/in/gui");
        List<String> proibidos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String linha : Files.readAllLines(arquivo)) {
                    String t = linha.trim();
                    if (!t.startsWith("import br.com.") || t.startsWith("import br.com.adapter.in.gui")) {
                        continue;
                    }
                    String importado = t.replace("import ", "").replace(";", "");
                    if (PERMITIDOS.stream().noneMatch(importado::equals)) {
                        proibidos.add(arquivo.getFileName() + " importa " + importado);
                    }
                }
            }
        }
        assertTrue(proibidos.isEmpty(), "A GUI só pode falar com a API. Imports proibidos: " + proibidos);
    }
}
