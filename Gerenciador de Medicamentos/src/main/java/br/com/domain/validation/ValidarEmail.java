package br.com.domain.validation;

import java.util.regex.Pattern;

public class ValidarEmail {
    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public static boolean validar(String email){
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
