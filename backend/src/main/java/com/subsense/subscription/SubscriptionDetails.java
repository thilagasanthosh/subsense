package com.subsense.subscription;

import com.subsense.currency.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** What the user enters for a subscription. Field rules here; cross-field rules in the service. */
public record SubscriptionDetails(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String vendor,
    @NotNull Long categoryId,
    @NotNull SubscriptionType type,
    @NotNull BillingCycle billingCycle,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
    @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceAfterTrial,
    @NotNull CurrencyCode currency,
    @NotNull LocalDate startDate,
    @NotNull LocalDate nextRenewalDate,
    @Min(0) @Max(365) int noticeDays,
    boolean autoRenew,
    // A label such as "Visa ending 4417". At most 4 digits, so a card number can never be stored.
    @Size(max = 50)
        @Pattern(
            regexp = "^[^0-9]*([0-9][^0-9]*){0,4}$",
            message = "must be a label with at most 4 digits, never a card number")
        String paymentLabel,
    @NotBlank @Size(max = 50) String owner,
    UsageRating usageRating,
    @Size(max = 1000) String notes) {}
