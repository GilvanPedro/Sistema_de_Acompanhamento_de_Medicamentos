package br.com.adapter.out.id;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import br.com.domain.port.out.GerarIdPort;

public class GerarIdPorArquivoAdapter implements GerarIdPort {

    private int contador;

    public GerarIdPorArquivoAdapter(String arquivo) {
        this.contador = calcularProximoId(arquivo);
    }

    @Override
    public synchronized int proximoId() {
        return contador++;
    }

    private int calcularProximoId(String arquivo) {
        Path caminho = Paths.get(arquivo);
        if (!Files.exists(caminho)) {
            return 1;
        }

        int maiorId = 0;
        try {
            List<String> linhas = Files.readAllLines(caminho);
            for (String linha : linhas) {
                if (linha.isBlank()) continue;
                int id = Integer.parseInt(linha.split(";")[0]);
                if (id > maiorId) {
                    maiorId = id;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler " + arquivo + " para calcular o próximo id", e);
        }

        return maiorId + 1;
    }
}