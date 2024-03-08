package io.trino.eventlistener.rabbitmq;

public class PublicationException extends RuntimeException {
    public PublicationException(String msg) {
        super(msg);
    }
}
