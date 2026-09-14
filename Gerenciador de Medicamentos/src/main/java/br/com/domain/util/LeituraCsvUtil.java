package br.com.domain.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class LeituraCsvUtil {

    public static <T> Optional<T> buscarPrimeiro(String caminhoArquivo, Predicate<String> condicao, Function<String, T> mapeador) {
        Path caminho = Paths.get(caminhoArquivo);
        if (!Files.exists(caminho)) {
            return Optional.empty();
        }

        try (BufferedReader reader = Files.newBufferedReader(caminho)) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (condicao.test(linha)) {
                    // Parada antecipada: retorna assim que encontra o registro desejado
                    return Optional.of(mapeador.apply(linha));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler o arquivo " + caminhoArquivo, e);
        }

        return Optional.empty();
    }
}