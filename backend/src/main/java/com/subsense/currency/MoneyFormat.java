package com.subsense.currency;

import java.math.BigDecimal;

/**
 * Formats money for people to read (notifications), rounding half-even only here. Grouping is done
 * by hand because java.text cannot group rupees as 1,23,456.
 */
public final class MoneyFormat {

  private MoneyFormat() {}

  public static String format(Money money) {
    BigDecimal amount = money.rounded().amount();
    String plain = amount.abs().toPlainString();
    int point = plain.indexOf('.');
    String whole = plain.substring(0, point);
    String fraction = plain.substring(point + 1);
    String sign = amount.signum() < 0 ? "-" : "";
    CurrencyCode currency = money.currency();
    return sign + currency.symbol() + group(whole, currency.laterGroupSize()) + "." + fraction;
  }

  private static String group(String digits, int laterGroupSize) {
    if (digits.length() <= 3) {
      return digits;
    }
    String head = digits.substring(0, digits.length() - 3);
    StringBuilder grouped = new StringBuilder(digits.substring(digits.length() - 3));
    while (!head.isEmpty()) {
      int cut = Math.max(0, head.length() - laterGroupSize);
      grouped.insert(0, head.substring(cut) + ",");
      head = head.substring(0, cut);
    }
    return grouped.toString();
  }
}
