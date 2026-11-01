package com.subsense.currency;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx-rates")
@Tag(name = "Exchange rates")
class FxRateController {

  private final FxRateService fxRateService;

  FxRateController(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  @GetMapping
  @Operation(summary = "Current cached exchange rates (per euro) and their date")
  FxRatesResponse current() {
    return FxRatesResponse.from(fxRateService.current());
  }

  @PostMapping("/refresh")
  @Operation(summary = "Fetch the latest rates now; returns 503 if the rate service is down")
  ResponseEntity<FxRatesResponse> refresh() {
    boolean refreshed = fxRateService.refresh();
    FxRatesResponse body = FxRatesResponse.from(fxRateService.current());
    return refreshed
        ? ResponseEntity.ok(body)
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }

  record FxRatesResponse(LocalDate asOf, String base, Map<String, String> rates) {

    static FxRatesResponse from(ExchangeRates rates) {
      Map<String, String> asText = new TreeMap<>();
      rates.perEuro().forEach((currency, rate) -> asText.put(currency.name(), plain(rate)));
      return new FxRatesResponse(rates.asOf(), FxRate.EURO, asText);
    }

    private static String plain(BigDecimal rate) {
      return rate.stripTrailingZeros().toPlainString();
    }
  }
}
