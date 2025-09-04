package br.gov.pb.der.netnotify.utils;

public class SimpleResponseUtils<T> {

    public enum Status {
        SUCCESS("success"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private Status status;
    private T object;
    private String message;

    public SimpleResponseUtils() {
        super();
    }

    public SimpleResponseUtils(T object) {
        this.object = object;
    }

    public SimpleResponseUtils(Status status, T object, String message) {
        this.status = status;
        this.object = object;
        this.message = message;
    }

    public T getObject() {
        return object;
    }

    public void setObject(T object) {
        this.object = object;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    // Métodos estáticos de conveniência
    public static <T> SimpleResponseUtils<T> success(T object) {
        return new SimpleResponseUtils<>(Status.SUCCESS, object, "Operation completed successfully");
    }

    public static <T> SimpleResponseUtils<T> success(T object, String message) {
        return new SimpleResponseUtils<>(Status.SUCCESS, object, message);
    }

    public static <T> SimpleResponseUtils<T> error(T object, String message) {
        return new SimpleResponseUtils<>(Status.ERROR, object, message);
    }

    public static <T> SimpleResponseUtils<T> warning(T object, String message) {
        return new SimpleResponseUtils<>(Status.WARNING, object, message);
    }

    public static <T> SimpleResponseUtils<T> info(T object, String message) {
        return new SimpleResponseUtils<>(Status.INFO, object, message);
    }

    public static <T> SimpleResponseUtils<T> getResponse(Status status, T object, String message) {
        return new SimpleResponseUtils<>(status, object, message);
    }

    // Métodos de conveniência para verificação de status
    public boolean isSuccess() {
        return Status.SUCCESS.equals(this.status);
    }

    public boolean isError() {
        return Status.ERROR.equals(this.status);
    }

    public boolean isWarning() {
        return Status.WARNING.equals(this.status);
    }

    public boolean isInfo() {
        return Status.INFO.equals(this.status);
    }
}
