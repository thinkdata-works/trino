package io.trino.eventlistener.rabbitmq;

public class ConnectionException extends RuntimeException {
    public ConnectionException(String msg) {
        super(msg);
    }
}
