package br.com.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê configurações sensíveis (senha do banco, segredo do JWT...) sem colocá-las no código:
 * primeiro da variável de ambiente e, se não existir, do arquivo {@code .env} na pasta onde o programa roda
 * ou na pasta acima dela (a raiz do repositório). O {@code .env} não vai para o Git.
 */
public final class Ambiente {

    // Na raiz do repositório; se o programa rodar de dentro da pasta do módulo, um nível acima.
    private static final List<Path> ARQUIVOS_ENV = List.of(Path.of(".env"), Path.of("..", ".env"));

    private static Map<String, String> valoresDoArquivo;

    private Ambiente() {
    }

    public static synchronized String valor(String nome) {
        String doAmbiente = System.getenv(nome);
        if (doAmbiente != null && !doAmbiente.isBlank()) {
            return doAmbiente;
        }
        if (valoresDoArquivo == null) {
            valoresDoArquivo = lerArquivoEnv();
        }
        return valoresDoArquivo.get(nome);
    }

    public static String valor(String nome, String padrao) {
        String valor = valor(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private static Map<String, String> lerArquivoEnv() {
        Map<String, String> valores = new HashMap<>();
        Path arquivo = ARQUIVOS_ENV.stream().filter(Files::isRegularFile).findFirst().orElse(null);
        if (arquivo == null) {
            return valores;
        }
        try {
            for (String linha : Files.readAllLines(arquivo)) {
                String limpa = linha.trim();
                int igual = limpa.indexOf('=');
                if (limpa.isEmpty() || limpa.startsWith("#") || igual <= 0) {
                    continue;
                }
                String valor = limpa.substring(igual + 1).trim();
                if (valor.length() >= 2 && (valor.startsWith("\"") && valor.endsWith("\"")
                        || valor.startsWith("'") && valor.endsWith("'"))) {
                    valor = valor.substring(1, valor.length() - 1);
                }
                valores.put(limpa.substring(0, igual).trim(), valor);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o arquivo .env");
        }
        return valores;
    }
}
