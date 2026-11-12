package com.bookeasy.notification.channel;

/** Outbound email channel. Implementations throw {@link DeliveryException} when the message was not accepted. */
public interface EmailSender {

    /** Content id the HTML uses for the attached QR image: {@code <img src="cid:bookeasy-qr">}. */
    String QR_CID = "bookeasy-qr";

    /**
     * htmlBody: optional HTML version (the text body is always sent too). qrPng: optional QR image shown in the HTML at
     * {@code cid:bookeasy-qr}; qrUrl: the same image on the web, for senders that can't attach inline images.
     */
    record EmailMessage(String to, String subject, String textBody, String htmlBody, byte[] qrPng, String qrUrl) {
        public EmailMessage(String to, String subject, String textBody) {
            this(to, subject, textBody, null, null, null);
        }
    }

    void send(EmailMessage message);

    /** False only for the development stand-in that writes emails to the log (enables on-screen demo codes). */
    default boolean deliversToInbox() {
        return true;
    }
}
