-- E-ticket emails: an HTML version of the message and the booking pass whose QR code is drawn into it when sent.
ALTER TABLE notification_outbox
    ADD COLUMN html_body  MEDIUMTEXT   NULL,
    ADD COLUMN qr_payload VARCHAR(160) NULL;
