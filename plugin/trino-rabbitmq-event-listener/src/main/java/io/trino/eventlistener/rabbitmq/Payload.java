package io.trino.eventlistener.rabbitmq;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.HashMap;
import java.util.Map;

public class Payload {
    private final Map<String, Object> payload = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getPayload() {
        return payload;
    }
}

