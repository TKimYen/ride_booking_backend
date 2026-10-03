package com.ridebooking.payment;

import com.ridebooking.payment.config.BookingClientProperties;
import com.ridebooking.payment.config.JwtProperties;
import com.ridebooking.payment.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, StripeProperties.class, BookingClientProperties.class})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
