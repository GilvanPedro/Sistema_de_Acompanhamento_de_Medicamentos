package br.com.domain.util;

import br.com.domain.exception.DadosInvalidosException;

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

    /** O separador do CSV é ';': um nome com ';' (ou quebra de linha) quebraria a linha do arquivo e corromperia os dados. */
    public static void exigirSemSeparador(String valor, String rotulo) {
        if (valor != null && (valor.indexOf(';') >= 0 || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0)) {
            throw new DadosInvalidosException("O " + rotulo + " não pode ter ponto e vírgula nem quebra de linha.");
        }
    }

    private static void criarPasta(Path caminho) throws IOException {
        Path pasta = caminho.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
    }

    public static void escreverLinha(String arquivo, String linha) {
        try {
            Path caminho = Paths.get(arquivo);
            criarPasta(caminho);
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
        try {
            criarPasta(Paths.get(arquivo));
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar a pasta de " + arquivo, e);
        }
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