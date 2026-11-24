package ru.mirea.storeapi.error;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Неверные учётные данные или доступ временно заблокирован");
    }
}
