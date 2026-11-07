package com.bookeasy.notification.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Msg91SmsSenderTest {

    @Test
    void sendsTheDltTemplateWithTheCodeAndAuthKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Msg91SmsSender sender = new Msg91SmsSender(builder, "https://control.msg91.com", "key-123", "tmpl-9", "otp");

        server.expect(requestTo("https://control.msg91.com/api/v5/flow"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authkey", "key-123"))
                .andExpect(content().json("""
                        {"template_id":"tmpl-9","short_url":"0","recipients":[{"mobiles":"919876543210","otp":"482913"}]}
                        """))
                .andRespond(withSuccess("{\"type\":\"success\"}", MediaType.APPLICATION_JSON));

        sender.sendOtp("+919876543210", "482913");
        server.verify();
    }

    @Test
    void providerErrorsBecomeDeliveryExceptions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Msg91SmsSender sender = new Msg91SmsSender(builder, "https://control.msg91.com", "k", "t", "otp");
        server.expect(requestTo("https://control.msg91.com/api/v5/flow")).andRespond(withServerError());

        assertThatThrownBy(() -> sender.sendOtp("+919876543210", "111111")).isInstanceOf(DeliveryException.class);
    }
}
