package io.trino.eventlistener.rabbitmq;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import io.trino.eventlistener.rabbitmq.PublicationException;
import io.trino.eventlistener.rabbitmq.RabbitmqClient;
import io.trino.eventlistener.rabbitmq.Payload;
import io.trino.spi.eventlistener.EventListener;
import io.trino.spi.eventlistener.QueryCompletedEvent;
import io.trino.spi.eventlistener.QueryCreatedEvent;
import io.trino.spi.eventlistener.SplitCompletedEvent;

import java.util.List;
import java.util.Map;


public class RabbitmqEventListener implements EventListener {
    private final RabbitmqEventListenerConfig config;
    private final RabbitmqClient client;

    public RabbitmqEventListener(RabbitmqEventListenerConfig config) {
        this.config = config;
        this.client = new RabbitmqClient(
                config.getUrl(),
                config.getExchangeName(),
                config.getExchangeType(),
                config.isDurableExchange(),
                config.shouldSuppressConnectionErrors()
        );
    }

    @Override
    public void queryCompleted(final QueryCompletedEvent queryCompletedEvent) {
        if (config.shouldPublishQueryCompleted()) {
            try {
                client.Publish(this.config.getQueryCompletedQueues(), serializePayload(queryCompletedEvent));
            } catch (Exception e) {
                // Print error and continue so that Trino behaviour is not interrupted
                System.err.println("Attempted to publish message but got " + e.getClass() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void queryCreated(final QueryCreatedEvent queryCreatedEvent) {
        if (config.shouldPublishQueryCreated()) {
            try {
                client.Publish(this.config.getQueryCreatedQueues(), serializePayload(queryCreatedEvent));
            } catch (Exception e) {
                // Print error and continue so that Trino behaviour is not interrupted
                System.err.println("Attempted to publish message but got " + e.getClass() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void splitCompleted(final SplitCompletedEvent splitCompletedEvent) {
        if (config.shouldPublishSplitCompleted()) {
            try {
                client.Publish(this.config.getSplitCompletedQueues(), serializePayload(splitCompletedEvent));
            } catch (Exception e) {
                // Print error and continue so that Trino behaviour is not interrupted
                System.err.println("Attempted to publish message but got " + e.getClass() + ": " + e.getMessage());
            }
        }
    }

    private byte[] serializePayload(Object val) throws PublicationException {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JSR310Module())
                    .registerModule(new Jdk8Module());

            if(config.getPayloadParentKeys().size() < 1) {
                return mapper.writeValueAsBytes(val);
            } else {
                return mapper.writeValueAsBytes(
                        constructPayload(config.getPayloadParentKeys(), config.getCustomProperties(), val)
                );
            }
        } catch (JsonProcessingException e) {
            throw new PublicationException("Got JSON processing error payload: " + e.getMessage());
        }
    }

    static Payload constructPayload(List<String> allKeys, Map<String, String> customProps, Object val) {
        Payload rootpayload = new Payload();
        Payload lastchild = rootpayload;

        for(int i = 0; i < allKeys.size(); i++) {
            if (i == allKeys.size() - 1) {
                // If we are on the last element, append the input payload
                lastchild.getPayload().put(allKeys.get(i), val);
                // And add our custom properties
                for(Map.Entry<String, String> prop : customProps.entrySet()) {
                    lastchild.getPayload().put(prop.getKey(), prop.getValue());
                }
            } else {
                // Otherwise create a new payload and move the parent chain
                Payload newPayload = new Payload();
                lastchild.getPayload().put(allKeys.get(i), newPayload);
                lastchild = newPayload;
            }
        }

        return rootpayload;
    }
}
