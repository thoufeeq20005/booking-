package com.bookeasy.notification.channel;

/** Outbound email channel. Implementations throw {@link DeliveryException} when the message was not accepted. */
public interface EmailSender {

    record EmailMessage(String to, String subject, String textBody) {
    }

    void send(EmailMessage message);
}
