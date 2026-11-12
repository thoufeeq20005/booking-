package com.bookeasy.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

import com.bookeasy.appointment.AppointmentView;

/**
 * The e-ticket sent when a booking is confirmed: what was booked, when and where, seats or guests, what was paid, and
 * the booking pass QR (drawn in when the email is sent, see {@link NotificationService#dispatch()}). Labels come from
 * {@code ticket.*} messages in the customer's language; the HTML uses tables and inline styles so mail apps render it.
 */
public final class TicketEmail {

    private TicketEmail() {
    }

    /** html and text versions, and the signed pass whose QR the email shows. */
    public record Ticket(String html, String text, String qrPayload) {
    }

    enum Kind { MOVIE, DINING, APPOINTMENT }

    static Kind kind(AppointmentView v) {
        if (v.showtimeId() != null) {
            return Kind.MOVIE;
        }
        return v.partySize() != null ? Kind.DINING : Kind.APPOINTMENT;
    }

    public static Ticket render(AppointmentView v, Locale locale, MessageSource messages, String frontendUrl, String bookingPath) {
        Labels l = new Labels(messages, locale);
        Kind kind = kind(v);
        ZoneId zone = ZoneId.of(v.timezone() == null ? "Asia/Kolkata" : v.timezone());
        String when = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", locale).withZone(zone).format(v.startsAt());
        String where = v.organizationName() + (v.branchName() == null ? "" : " · " + v.branchName());
        String site = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{l.get("ticket.when"), when});
        rows.add(new String[]{l.get("ticket.where"), where + (v.branchAddress() == null ? "" : "\n" + v.branchAddress())});
        if (kind == Kind.MOVIE) {
            if (v.screenName() != null) {
                rows.add(new String[]{l.get("ticket.screen"), v.screenName()});
            }
            if (v.seats() != null && !v.seats().isEmpty()) {
                rows.add(new String[]{l.get("ticket.seats"), v.seats().size() + " · " + String.join(", ", v.seats())});
            }
        } else if (kind == Kind.DINING) {
            rows.add(new String[]{l.get("ticket.guests"), String.valueOf(v.partySize())});
            if (v.offerTitle() != null) {
                rows.add(new String[]{l.get("ticket.offer"), v.offerTitle()});
            }
        } else {
            if (v.staffName() != null) {
                rows.add(new String[]{l.get("ticket.with"), v.staffName()});
            }
            if (v.patientName() != null) {
                rows.add(new String[]{l.get("ticket.for"), v.patientName()});
            }
        }

        List<String[]> money = new ArrayList<>();
        if (v.totalAmount() != null && v.totalAmount().signum() > 0) {
            if (kind == Kind.DINING) {
                money.add(new String[]{l.get("ticket.coverCharge"), rupees(v.servicePrice())});
            } else {
                money.add(new String[]{l.get(kind == Kind.MOVIE ? "ticket.tickets" : "ticket.price"), rupees(v.servicePrice())});
                if (v.platformFee() != null && v.platformFee().signum() > 0) {
                    money.add(new String[]{l.get("ticket.fee"), rupees(v.platformFee())});
                }
            }
            money.add(new String[]{l.get(v.paid() ? "ticket.amountPaid" : "ticket.amountDue"), rupees(v.totalAmount())});
        }

        String heading = l.get("ticket.heading." + kind.name().toLowerCase(Locale.ROOT));
        String viewUrl = site + bookingPath;
        return new Ticket(html(l, heading, v, rows, money, viewUrl), text(l, heading, v, rows, money, viewUrl), v.qrPayload());
    }

    static String rupees(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        f.setMinimumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        f.setMaximumFractionDigits(2);
        return "₹" + f.format(amount);
    }

    private static String text(Labels l, String heading, AppointmentView v, List<String[]> rows, List<String[]> money, String viewUrl) {
        StringBuilder t = new StringBuilder();
        t.append(heading).append("\n\n").append(v.serviceName()).append('\n');
        t.append(l.get("ticket.reference")).append(": ").append(v.reference()).append("\n\n");
        for (String[] r : rows) {
            t.append(r[0]).append(": ").append(r[1].replace("\n", ", ")).append('\n');
        }
        if (!money.isEmpty()) {
            t.append('\n');
            for (String[] r : money) {
                t.append(r[0]).append(": ").append(r[1]).append('\n');
            }
        }
        t.append('\n').append(l.get("ticket.showQr")).append('\n').append(viewUrl).append("\n\n– BookEasy");
        return t.toString();
    }

    private static String html(Labels l, String heading, AppointmentView v, List<String[]> rows, List<String[]> money, String viewUrl) {
        StringBuilder details = new StringBuilder();
        for (String[] r : rows) {
            details.append("<tr><td style=\"padding:6px 0;color:#64748b;font-size:13px;vertical-align:top;width:38%\">")
                    .append(e(r[0])).append("</td><td style=\"padding:6px 0;color:#0f172a;font-size:14px;font-weight:600\">")
                    .append(e(r[1]).replace("\n", "<br><span style=\"font-weight:400;color:#475569;font-size:13px\">"))
                    .append(r[1].contains("\n") ? "</span>" : "").append("</td></tr>");
        }
        StringBuilder pay = new StringBuilder();
        for (int i = 0; i < money.size(); i++) {
            boolean total = i == money.size() - 1;
            pay.append("<tr><td style=\"padding:4px 0;font-size:").append(total ? "15px;font-weight:700;color:#0f172a" : "13px;color:#475569")
                    .append("\">").append(e(money.get(i)[0])).append("</td><td align=\"right\" style=\"padding:4px 0;font-size:")
                    .append(total ? "15px;font-weight:700;color:#0f172a" : "13px;color:#475569").append("\">")
                    .append(e(money.get(i)[1])).append("</td></tr>");
        }
        String payBlock = money.isEmpty() ? "" : """
                <tr><td style="padding:0 28px"><div style="border-top:1px dashed #cbd5e1"></div></td></tr>
                <tr><td style="padding:14px 28px 4px"><table role="presentation" width="100%%" cellpadding="0" cellspacing="0">%s</table></td></tr>
                """.formatted(pay);
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden">
                  <tr><td style="background:#2563eb;padding:20px 28px;color:#ffffff">
                    <div style="font-size:20px;font-weight:800;letter-spacing:-0.2px">Book<span style="color:#bfdbfe">Easy</span></div>
                    <div style="margin-top:6px;font-size:15px;opacity:.95">%1$s</div>
                  </td></tr>
                  <tr><td style="padding:24px 28px 8px">
                    <div style="font-size:22px;font-weight:800;color:#0f172a;line-height:1.25">%2$s</div>
                    <div style="margin-top:4px;font-size:13px;color:#64748b">%3$s: <b style="color:#0f172a;letter-spacing:.5px">%4$s</b></div>
                  </td></tr>
                  <tr><td style="padding:8px 28px 16px"><table role="presentation" width="100%%" cellpadding="0" cellspacing="0">%5$s</table></td></tr>
                  <tr><td align="center" style="padding:8px 28px 20px">
                    <div style="display:inline-block;border:1px solid #e2e8f0;border-radius:14px;padding:12px">
                      <img src="cid:%6$s" width="180" height="180" alt="%7$s" style="display:block;width:180px;height:180px">
                    </div>
                    <div style="margin-top:10px;font-size:13px;color:#475569">%7$s</div>
                  </td></tr>
                  %8$s
                  <tr><td align="center" style="padding:20px 28px 28px">
                    <a href="%9$s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:700;font-size:14px;padding:12px 28px;border-radius:999px">%10$s</a>
                  </td></tr>
                </table>
                <div style="max-width:560px;margin-top:14px;font-size:12px;color:#94a3b8;text-align:center">%11$s</div>
                </td></tr></table>
                </body></html>
                """.formatted(e(heading), e(v.serviceName()), e(l.get("ticket.reference")), e(v.reference()), details,
                com.bookeasy.notification.channel.EmailSender.QR_CID, e(l.get("ticket.showQr")), payBlock, e(viewUrl),
                e(l.get("ticket.viewBooking")), e(l.get("ticket.footer")));
    }

    private static String e(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private record Labels(MessageSource messages, Locale locale) {
        String get(String key) {
            return messages.getMessage(key, null, key, locale);
        }
    }
}
