package br.com.adapter.in.console;

public class EntradaUtil {
    private EntradaUtil() { }

    public static boolean pareceId(String entrada) {
        try {
            Integer.parseInt(entrada.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}