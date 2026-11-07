package com.bookeasy.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Razorpay over HTTPS with the key id/secret as Basic auth. Amounts are always in paise. */
@Component
public class HttpRazorpayClient implements RazorpayClient {

    private final RestClient client;

    public HttpRazorpayClient(PaymentProperties properties) {
        PaymentProperties.Razorpay r = properties.razorpay();
        String base = r == null || r.baseUrl() == null || r.baseUrl().isBlank() ? "https://api.razorpay.com" : r.baseUrl();
        RestClient.Builder builder = RestClient.builder().baseUrl(base);
        if (properties.enabled()) {
            builder.defaultHeaders(h -> h.setBasicAuth(r.keyId(), r.keySecret()));
        }
        this.client = builder.build();
    }

    @Override
    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        return post("/v1/orders", Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt,
                "payment_capture", 1, "notes", notes));
    }

    @Override
    public String refund(String paymentId, long amountPaise, Map<String, String> notes) {
        return post("/v1/payments/" + paymentId + "/refund", Map.of("amount", amountPaise, "notes", notes));
    }

    @Override
    public String transfer(String paymentId, String linkedAccountId, long amountPaise) {
        Map<String, Object> t = new HashMap<>();
        t.put("account", linkedAccountId);
        t.put("amount", amountPaise);
        t.put("currency", "INR");
        Map<?, ?> res = call("/v1/payments/" + paymentId + "/transfers", Map.of("transfers", List.of(t)));
        Object items = res.get("items");
        if (items instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return String.valueOf(first.get("id"));
        }
        throw new RazorpayException("Transfer response without id", null);
    }

    private String post(String path, Map<String, ?> body) {
        Object id = call(path, body).get("id");
        if (id == null) {
            throw new RazorpayException("Razorpay response without id for " + path, null);
        }
        return id.toString();
    }

    private Map<?, ?> call(String path, Map<String, ?> body) {
        try {
            Map<?, ?> res = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            if (res == null) {
                throw new RazorpayException("Empty Razorpay response for " + path, null);
            }
            return res;
        } catch (RestClientException e) {
            throw new RazorpayException("Razorpay call failed: " + path, e);
        }
    }
}
