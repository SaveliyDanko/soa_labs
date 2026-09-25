package ru.itmo.soa.error;

public final class ValidationMessages {
    public static final String REQUIRED = "Поле обязательно для заполнения";
    public static final String NOT_BLANK = "Значение не должно быть пустым или состоять из пробелов";
    public static final String POSITIVE = "Значение должно быть больше нуля";

    private ValidationMessages() {}
}
