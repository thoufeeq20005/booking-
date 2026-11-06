package com.bookeasy.payment;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;
import com.bookeasy.payment.PaymentService.CheckoutOrder;
import com.bookeasy.payment.PaymentService.Receipt;
import com.bookeasy.payment.SettlementService.Earnings;
import com.bookeasy.payment.SettlementService.SettlementView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService payments;
    private final SettlementService settlements;
    private final ClientIpResolver ip;

    public PaymentController(PaymentService payments, SettlementService settlements, ClientIpResolver ip) {
        this.payments = payments;
        this.settlements = settlements;
        this.ip = ip;
    }

    public record VerifyBody(@NotBlank @Size(max = 64) String orderId, @NotBlank @Size(max = 64) String paymentId,
                             @NotBlank @Size(max = 128) String signature) {
    }

    public record ReferenceBody(@NotBlank @Size(max = 100) String reference) {
    }

    public record AccountBody(@Size(max = 64) String accountId) {
    }

    private static long me() {
        return CurrentUser.require().id();
    }

    @PostMapping("/customer/appointments/{id}/payment-order")
    @Operation(summary = "Create (or reuse) the Razorpay order for an unpaid booking")
    public ApiResponse<CheckoutOrder> order(@PathVariable long id) {
        return ApiResponse.ok(payments.createOrder(me(), id));
    }

    public record DemoPayBody(@jakarta.validation.constraints.NotBlank String method) {
    }

    @PostMapping("/customer/appointments/{id}/demo-payment")
    @Operation(summary = "Development only: complete the payment with the simulated gateway")
    public ApiResponse<Void> demoPay(@PathVariable long id, @Valid @RequestBody DemoPayBody b) {
        payments.completeDemo(me(), id, b.method());
        return ApiResponse.ok(null);
    }

    @PostMapping("/customer/payments/verify")
    @Operation(summary = "Verify the Razorpay Checkout signature and confirm the booking")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyBody b) {
        payments.verifyCheckout(me(), b.orderId(), b.paymentId(), b.signature());
        return ApiResponse.ok(null);
    }

    @GetMapping("/customer/appointments/{id}/receipt")
    public ApiResponse<Receipt> receipt(@PathVariable long id) {
        return ApiResponse.ok(payments.receipt(me(), id));
    }

    @PostMapping("/public/webhooks/razorpay")
    @SecurityRequirements
    @Operation(summary = "Razorpay webhook (HMAC-signed, de-duplicated by event id)")
    public ResponseEntity<ApiResponse<Void>> webhook(@RequestBody String body,
                                                     @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
                                                     @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        payments.webhook(body, signature, eventId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/owner/organizations/{orgId}/earnings")
    public ApiResponse<Earnings> earnings(@PathVariable long orgId) {
        return ApiResponse.ok(settlements.ownerEarnings(me(), orgId));
    }

    @GetMapping("/admin/settlements")
    public ApiResponse<List<SettlementView>> adminSettlements(@RequestParam(required = false) String status) {
        return ApiResponse.ok(settlements.adminList(status));
    }

    @PostMapping("/admin/settlements/run")
    @Operation(summary = "Run settlement now (normally daily at 02:00 IST)")
    public ApiResponse<Integer> run() {
        return ApiResponse.ok(settlements.settle());
    }

    @PostMapping("/admin/settlements/{id}/paid")
    public ApiResponse<SettlementView> markPaid(@PathVariable long id, @Valid @RequestBody ReferenceBody b, HttpServletRequest http) {
        return ApiResponse.ok(settlements.markPaid(CurrentUser.require(), id, b.reference(), ip.resolve(http)));
    }

    @PutMapping("/admin/organizations/{orgId}/payout-account")
    @Operation(summary = "Set the business's Razorpay Route linked account (acc_…)")
    public ApiResponse<Void> account(@PathVariable long orgId, @Valid @RequestBody AccountBody b, HttpServletRequest http) {
        settlements.setLinkedAccount(CurrentUser.require(), orgId, b.accountId(), ip.resolve(http));
        return ApiResponse.ok(null);
    }
}
