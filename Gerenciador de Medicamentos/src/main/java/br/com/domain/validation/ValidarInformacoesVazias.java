package br.com.domain.validation;

public class ValidarInformacoesVazias {
    public static boolean validar(String informacao){
        if (informacao == null || informacao.isBlank()) {
            return false;
        } else {
            return true;
        }
    }
}
