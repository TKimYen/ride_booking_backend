package com.ridebooking.booking;

import com.ridebooking.booking.config.JwtProperties;
import com.ridebooking.booking.enums.BusType;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class BookingServiceApplication {

	public static void main(String[] args) {
		verifySeatLayouts();
		SpringApplication.run(BookingServiceApplication.class, args);
	}

	/**
	 * Dừng service ngay lúc khởi động nếu sơ đồ ghế không khớp số chỗ khai trong
	 * tên loại xe.
	 *
	 * <p>Đã có một lần tên {@code LIMOUSINE_29} sinh ra 49 ghế. Nếu không chặn ở
	 * đây thì chuyến xe sẽ được bán với 49 chỗ trong khi UI hiện "29 chỗ", và
	 * lỗi chỉ lộ ra khi khách bấm nhầm chỗ — rất khó truy ra nguyên nhân.
	 */
	private static void verifySeatLayouts() {
		List<String> problems = BusType.verifyLayoutsAreConsistent();
		if (!problems.isEmpty()) {
			throw new IllegalStateException(
					"Sơ đồ ghế không nhất quán, không cho khởi động booking-service:" + problems);
		}
	}

}