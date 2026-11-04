package com.bookeasy.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bookeasy.notification.channel.EmailSender;
import com.bookeasy.notification.channel.SmsSender;

/** Captures outbound emails and SMS so tests can read links and codes. */
public class RecordingChannels implements EmailSender, SmsSender {

    public record Sms(String phone, String code) {
    }

    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_\\-%]+)");

    private final List<EmailMessage> emails = new CopyOnWriteArrayList<>();
    private final List<Sms> sms = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        emails.add(message);
    }

    @Override
    public void sendOtp(String phoneE164, String code) {
        sms.add(new Sms(phoneE164, code));
    }

    public void clear() {
        emails.clear();
        sms.clear();
    }

    public List<EmailMessage> emailsTo(String address) {
        return emails.stream().filter(e -> e.to().equals(address)).toList();
    }

    /** Token from the most recent email to the address. */
    public String lastTokenFor(String address) {
        List<EmailMessage> mine = emailsTo(address);
        if (mine.isEmpty()) {
            throw new AssertionError("No email sent to " + address);
        }
        Matcher m = TOKEN.matcher(mine.get(mine.size() - 1).textBody());
        if (!m.find()) {
            throw new AssertionError("No token link in email to " + address + ":\n" + mine.get(mine.size() - 1).textBody());
        }
        return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }

    public String lastCodeFor(String phone) {
        return sms.stream().filter(s -> s.phone().equals(phone)).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("No SMS sent to " + phone)).code();
    }

    public long smsCountFor(String phone) {
        return sms.stream().filter(s -> s.phone().equals(phone)).count();
    }
}
