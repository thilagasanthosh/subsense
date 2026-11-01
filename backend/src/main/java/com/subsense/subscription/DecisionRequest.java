package com.subsense.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DecisionRequest(
    @NotNull Decision decision,
    @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal newAmount,
    @Size(max = 500) String reason) {}
