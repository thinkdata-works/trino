package io.trino.eventlistener.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class RabbitmqClient {
    private Channel channel;
    private String uri;
    private String exchangeName;
    private String exchangeType;
    private boolean durable;

    private boolean suppressConnectionErrors;

    public RabbitmqClient(String url, String exchangeName, String exchangeType, boolean durable, boolean suppressConnectionErrors) {
        this.exchangeName = exchangeName;
        this.uri = url;
        this.exchangeType = exchangeType;
        this.durable = durable;
        this.suppressConnectionErrors = suppressConnectionErrors;

        System.out.println("Creating Rabbitmq connection, with suppress-connection-errors: " + suppressConnectionErrors);
        try {
            this.establishConnection();
        } catch(TimeoutException | IOException e) {
            if (this.suppressConnectionErrors) {
                System.err.println("Received error when creating rabbitmq connection "+ e.getClass() + ": " + e.getMessage());
                System.err.println("Connection re-attempt will be made at time of publication");
            } else {
                throw new ConnectionException(e.getMessage());
            }
        }
    }

    private static ConnectionFactory getConnectionFactory(String uri) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            return factory;
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new ConnectionException(e.getMessage());
        }
    }

    private void establishConnection() throws IOException, TimeoutException {
        this.channel = getConnectionFactory(uri).newConnection().createChannel();
        this.channel.exchangeDeclare(exchangeName, exchangeType, durable);
    }

    public void Publish(Set<String> queues, byte[] message) {
        synchronized(this) {
            // Ensure that we have a channel and it's open
            if (this.channel == null || !this.channel.isOpen()) {
                try {
                    this.establishConnection();
                } catch (IOException | TimeoutException e) {
                    if(this.suppressConnectionErrors) {
                        System.err.println("Attempted to create channel for publication but got error " + e.getClass() + ": " + e.getMessage());
                        System.err.println("Message will be discarded, and another attempt will be made at publication time");
                    } else {
                        throw new ConnectionException(e.getMessage());
                    }
                }
            }

            // If we have an open channel, publish
            for (String queueName: queues) {
                try {
                    this.channel.basicPublish(this.exchangeName, queueName, null, message);
                } catch(IOException e) {
                    if(this.suppressConnectionErrors) {
                        System.err.println("Attempted to publish message but got error " + e.getClass() + ": " + e.getMessage());
                    } else {
                        throw new PublicationException(e.getMessage());
                    }
                }
            }
        }
    }
}
