package com.smartbus.driver.utils;

public class Result<T> {

    private final T data;
    private final Throwable error;
    private final String message;

    private Result(T data, Throwable error, String message) {
        this.data = data;
        this.error = error;
        this.message = message;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data, null, null);
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<>(data, null, message);
    }

    public static <T> Result<T> error(Throwable error) {
        return new Result<>(null, error, error != null ? error.getMessage() : null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(null, new Exception(message), message);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public T getData() {
        return data;
    }

    public Throwable getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}
