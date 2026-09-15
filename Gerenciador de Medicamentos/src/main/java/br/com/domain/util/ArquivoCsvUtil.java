package br.com.domain.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ArquivoCsvUtil {

    public static List<String> lerLinhas(String arquivo) {
        Path caminho = Paths.get(arquivo);
        if (!Files.exists(caminho)) {
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(caminho);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler " + arquivo, e);
        }
    }

    public static void escreverLinha(String arquivo, String linha) {
        try {
            Path caminho = Paths.get(arquivo);
            boolean precisaQuebraAntes = false;

            if (Files.exists(caminho) && Files.size(caminho) > 0) {
                byte[] bytes = Files.readAllBytes(caminho);
                char ultimoChar = (char) bytes[bytes.length - 1];
                precisaQuebraAntes = ultimoChar != '\n';
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    caminho, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (precisaQuebraAntes) {
                    writer.newLine();
                }
                writer.write(linha);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar em " + arquivo, e);
        }
    }

    public static void reescreverLinhas(String arquivo, List<String> linhas) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(arquivo), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String linha : linhas) {
                writer.write(linha);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao reescrever " + arquivo, e);
        }
    }
}