package com.masmatix.suite.core.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock in the business's own time zone, shared by bookings, memberships and stays. */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Europe/London}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
