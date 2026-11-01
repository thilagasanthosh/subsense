package com.subsense.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now", so tests can fix the date and every rule uses the user's time zone. */
@Configuration
class ClockConfig {

  @Bean
  Clock clock(SubSenseProperties properties) {
    return Clock.system(properties.timeZone());
  }
}
