package com.bookeasy.notification.channel;

/** A provider did not accept a message, or no provider is configured for the channel. */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
