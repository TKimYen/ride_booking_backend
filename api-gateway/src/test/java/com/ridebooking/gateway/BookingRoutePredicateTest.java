package com.ridebooking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * Kiểm tra route đã đăng ký thật, không chỉ nhìn file YAML.
 *
 * <p>Ca kiểm thử này ra đời từ một lỗi thật: route booking-service liệt kê
 * {@code /api/admin/locations/**} nhưng thiếu {@code /api/admin/bookings/**}, nên
 * gọi qua gateway trả 404 trong khi gọi thẳng cổng 8083 vẫn 200. Frontend chỉ nói
 * chuyện với gateway, nên đó là lỗi hỏng tính năng dù service đúng.
 */
@SpringBootTest
class BookingRoutePredicateTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void adminBookingsIsRoutedToBookingService() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(routes).isNotNull();

        RouteDefinition bookingRoute = routes.stream()
                .filter(route -> "booking-service".equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có route booking-service"));

        assertThat(bookingRoute.getPredicates())
                .as("route booking-service phải khớp /api/admin/bookings/**")
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .anyMatch(value -> value.contains("/api/admin/bookings/**")));
    }
}
