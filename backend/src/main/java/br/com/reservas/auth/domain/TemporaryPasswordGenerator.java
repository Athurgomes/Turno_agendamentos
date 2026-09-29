package br.com.reservas.auth.domain;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * RN-03: senha temporaria de 6 caracteres, so letras maiusculas e numeros,
 * sem os caracteres ambiguos {@code 0 O 1 I L}. Exibida uma unica vez pelo
 * chamador; aqui so o texto plano e gerado (o hash BCrypt e responsabilidade
 * de quem grava a conta).
 */
@Component
public class TemporaryPasswordGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
