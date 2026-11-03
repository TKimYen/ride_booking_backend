package com.ridebooking.booking.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.booking.dto.HoldSeatsRequest;
import com.ridebooking.booking.dto.PaymentRequest;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.entity.Location;
import com.ridebooking.booking.entity.Route;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.BusType;
import com.ridebooking.booking.enums.TripStatus;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.repository.LocationRepository;
import com.ridebooking.booking.repository.RouteRepository;
import com.ridebooking.booking.repository.TripRepository;
import com.ridebooking.booking.repository.TripSeatRepository;
import com.ridebooking.booking.service.BookingService;
import com.ridebooking.booking.service.SeatGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra qua HTTP: lỗi 400 [L2], API xem/huỷ/hoàn thành chuyến [3.1, 3.2, 3.3],
 * thông tin nhà xe [4.1], tuyến phổ biến [4.3]. Đi qua cả bộ lọc bảo mật và handler
 * lỗi, nên bắt được cả sai quyền và sai mã trạng thái mà test tầng service không thấy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCatalogApiTest {

    private static final int USER = 101;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingService bookingService;
    @Autowired private SeatGenerator seatGenerator;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripSeatRepository tripSeatRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusCompanyRepository busCompanyRepository;
    @Autowired private LocationRepository locationRepository;

    private Route route;
    private BusCompany company;

    @BeforeEach
    void setUp() {
        Location from = locationRepository.save(location("Điểm đi api", "diem-di-api"));
        Location to = locationRepository.save(location("Điểm đến api", "diem-den-api"));
        BusCompany newCompany = new BusCompany();
        newCompany.setName("Nhà xe api");
        company = busCompanyRepository.save(newCompany);
        route = routeRepository.save(route("Tuyến api chính", from, to));
    }

    // ---- [L2] ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[L2] Dữ liệu sai làm service ném IllegalArgumentException -> 400 kèm thông điệp")
    void illegalArgumentBecomes400() throws Exception {
        String body = """
                {"routeId": %d, "companyId": %d, "busType": "LIMOUSINE_29",
                 "departureTime": "2030-10-05T10:00:00", "arrivalTime": "2030-10-05T08:00:00",
                 "basePrice": 250000}
                """.formatted(route.getId(), company.getId());

        mockMvc.perform(post("/api/admin/trips").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Giờ đến phải sau giờ khởi hành"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[L2] Loại xe lạ -> 400, liệt kê loại hợp lệ")
    void unknownBusTypeBecomes400() throws Exception {
        String body = """
                {"routeId": %d, "companyId": %d, "busType": "BUS_88",
                 "departureTime": "2030-10-05T06:00:00", "arrivalTime": "2030-10-05T08:00:00",
                 "basePrice": 250000}
                """.formatted(route.getId(), company.getId());

        mockMvc.perform(post("/api/admin/trips").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("LIMOUSINE_29")));
    }

    // ---- [3.3] Xem chuyến ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[3.3] Admin thấy chuyến ở mọi trạng thái, lọc được theo trạng thái")
    void adminListsTripsOfAnyStatus() throws Exception {
        trip(LocalDateTime.now().plusDays(3), TripStatus.ACTIVE);
        trip(LocalDateTime.now().plusDays(4), TripStatus.CANCELLED);

        mockMvc.perform(get("/api/admin/trips").param("companyId", company.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].trip.status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].availableSeats").value(BusType.LIMOUSINE_29.seatCount()));

        mockMvc.perform(get("/api/admin/trips")
                        .param("companyId", company.getId().toString()).param("status", "ACTIVE"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trip.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[3.3] Lọc theo khoảng ngày tính cả ngày cuối; khoảng ngược -> 400")
    void adminListFiltersByDate() throws Exception {
        trip(LocalDateTime.of(2030, 1, 10, 23, 30), TripStatus.ACTIVE);
        trip(LocalDateTime.of(2030, 1, 11, 6, 0), TripStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/trips").param("companyId", company.getId().toString())
                        .param("from", "2030-01-01").param("to", "2030-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/admin/trips").param("from", "2030-01-10").param("to", "2030-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[3.3] Chi tiết chuyến kèm số ghế và số đơn; không có chuyến -> 404")
    void adminGetsTripDetail() throws Exception {
        Trip trip = trip(LocalDateTime.now().plusDays(3), TripStatus.ACTIVE);
        holdAndPay(trip);

        mockMvc.perform(get("/api/admin/trips/{id}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.trip.id").value(trip.getId()))
                .andExpect(jsonPath("$.seats.unavailableSeats").value(1))
                .andExpect(jsonPath("$.bookingsByStatus.PAID").value(1));

        mockMvc.perform(get("/api/admin/trips/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Người không phải Admin -> 403 ở mọi API quản trị chuyến")
    void customerIsForbidden() throws Exception {
        Trip trip = trip(LocalDateTime.now().plusDays(3), TripStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/trips")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/trips/{id}/cancel", trip.getId())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/trips/{id}/complete", trip.getId())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chưa đăng nhập -> 401 ở API huỷ chuyến")
    void anonymousIsUnauthorized() throws Exception {
        Trip trip = trip(LocalDateTime.now().plusDays(3), TripStatus.ACTIVE);

        mockMvc.perform(post("/api/admin/trips/{id}/cancel", trip.getId())).andExpect(status().isUnauthorized());
    }

    // ---- [3.1] [3.2] qua HTTP ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[3.2] Huỷ chuyến trả danh sách hoàn tiền; huỷ chuyến đã hoàn thành -> 409")
    void cancelEndpoint() throws Exception {
        Trip active = trip(LocalDateTime.now().plusDays(3), TripStatus.ACTIVE);
        holdAndPay(active);
        mockMvc.perform(post("/api/admin/trips/{id}/cancel", active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refunds", hasSize(1)))
                .andExpect(jsonPath("$.refunds[0].gateway").value("VNPAY"));

        Trip completed = trip(LocalDateTime.now().minusDays(3), TripStatus.COMPLETED);
        mockMvc.perform(post("/api/admin/trips/{id}/cancel", completed.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[3.1] Hoàn thành chuyến; chuyến chưa khởi hành -> 409")
    void completeEndpoint() throws Exception {
        Trip departed = trip(LocalDateTime.now().minusHours(1), TripStatus.ACTIVE);
        mockMvc.perform(post("/api/admin/trips/{id}/complete", departed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.status").value("COMPLETED"));

        Trip upcoming = trip(LocalDateTime.now().plusDays(1), TripStatus.ACTIVE);
        mockMvc.perform(post("/api/admin/trips/{id}/complete", upcoming.getId()))
                .andExpect(status().isConflict());
    }

    // ---- [4.1] Thông tin nhà xe ----

    @Test
    @DisplayName("[4.1] Khách đọc được profile nhà xe không cần đăng nhập; nhà xe rỗng trả danh sách rỗng")
    void profileIsPublic() throws Exception {
        mockMvc.perform(get("/api/bus-companies/{id}/profile", company.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nhà xe api"))
                .andExpect(jsonPath("$.images", hasSize(0)))
                .andExpect(jsonPath("$.amenities", hasSize(0)))
                .andExpect(jsonPath("$.policies", hasSize(0)))
                .andExpect(jsonPath("$.cancellationRules", hasSize(0)));

        mockMvc.perform(get("/api/bus-companies/{id}/profile", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] Admin thêm, sửa, xoá ảnh và profile phản ánh đúng thứ tự")
    void imageCrud() throws Exception {
        Integer second = postForId("/api/admin/bus-companies/" + company.getId() + "/images",
                "{\"imageUrl\":\"https://cdn.test/2.jpg\",\"sortOrder\":2}");
        Integer first = postForId("/api/admin/bus-companies/" + company.getId() + "/images",
                "{\"imageUrl\":\"https://cdn.test/1.jpg\",\"caption\":\"Đầu\",\"sortOrder\":1}");

        mockMvc.perform(get("/api/bus-companies/{id}/profile", company.getId()))
                .andExpect(jsonPath("$.images[0].id").value(first))
                .andExpect(jsonPath("$.images[1].id").value(second));

        mockMvc.perform(put("/api/admin/bus-companies/{c}/images/{i}", company.getId(), second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.test/moi.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.test/moi.jpg"))
                .andExpect(jsonPath("$.sortOrder").value(1));

        mockMvc.perform(delete("/api/admin/bus-companies/{c}/images/{i}", company.getId(), first))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bus-companies/{id}/profile", company.getId()))
                .andExpect(jsonPath("$.images", hasSize(1)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] URL ảnh không phải http(s) -> 400")
    void imageRejectsNonHttpUrl() throws Exception {
        mockMvc.perform(post("/api/admin/bus-companies/{c}/images", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] Không sửa được mục của nhà xe khác qua URL của nhà xe mình")
    void cannotTouchItemOfAnotherCompany() throws Exception {
        BusCompany other = new BusCompany();
        other.setName("Nhà xe khác api");
        other = busCompanyRepository.save(other);
        Integer image = postForId("/api/admin/bus-companies/" + company.getId() + "/images",
                "{\"imageUrl\":\"https://cdn.test/1.jpg\"}");

        mockMvc.perform(delete("/api/admin/bus-companies/{c}/images/{i}", other.getId(), image))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] Chính sách: loại lạ -> 400, thêm và sửa được")
    void policyCrud() throws Exception {
        mockMvc.perform(post("/api/admin/bus-companies/{c}/policies", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyType\":\"KHONG_CO\",\"title\":\"A\",\"content\":\"B\"}"))
                .andExpect(status().isBadRequest());

        Integer id = postForId("/api/admin/bus-companies/" + company.getId() + "/policies",
                "{\"policyType\":\"LUGGAGE\",\"title\":\"Hành lý\",\"content\":\"20kg\"}");

        mockMvc.perform(put("/api/admin/bus-companies/{c}/policies/{p}", company.getId(), id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyType\":\"PET\",\"title\":\"Thú cưng\",\"content\":\"Có lồng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyType").value("PET"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] Mốc hoàn tiền: trùng số giờ -> 409, quá 100% -> 400, profile sắp mốc lớn trước")
    void cancellationRuleCrud() throws Exception {
        String path = "/api/admin/bus-companies/" + company.getId() + "/cancellation-rules";
        postForId(path, "{\"hoursBefore\":24,\"refundPercent\":50}");
        Integer big = postForId(path, "{\"hoursBefore\":72,\"refundPercent\":90}");

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hoursBefore\":24,\"refundPercent\":10}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hoursBefore\":1,\"refundPercent\":101}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(path + "/" + big).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hoursBefore\":24,\"refundPercent\":90}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/bus-companies/{id}/profile", company.getId()))
                .andExpect(jsonPath("$.cancellationRules[0].hoursBefore").value(72));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("[4.1] Tiện ích: trùng mã -> 409, gắn vào nhà xe, đang dùng thì không xoá được")
    void amenityCrud() throws Exception {
        Integer wifi = postForId("/api/admin/amenities", "{\"code\":\"wifi_api\",\"name\":\"Wi-Fi\",\"icon\":\"wifi\"}");
        mockMvc.perform(post("/api/admin/amenities").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIFI_API\",\"name\":\"Trùng\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/bus-companies/{c}/amenities", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amenityIds\":[" + wifi + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("WIFI_API"));
        mockMvc.perform(get("/api/bus-companies/{id}/profile", company.getId()))
                .andExpect(jsonPath("$.amenities[0].id").value(wifi));

        mockMvc.perform(delete("/api/admin/amenities/{id}", wifi)).andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/bus-companies/{c}/amenities", company.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amenityIds\":[999999]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/bus-companies/{c}/amenities", company.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amenityIds\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/amenities/{id}", wifi)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("[4.1] Người không phải Admin không sửa được nội dung nhà xe")
    void customerCannotEditContent() throws Exception {
        mockMvc.perform(post("/api/admin/bus-companies/{c}/images", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.test/1.jpg\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/amenities")).andExpect(status().isForbidden());
    }

    // ---- [4.3] Tuyến phổ biến ----

    @Test
    @DisplayName("[4.3] Không cần đăng nhập; tuyến nổi bật đứng đầu; minPrice chỉ tính chuyến sắp chạy còn bán")
    void popularRoutesRankAndPrice() throws Exception {
        Location from = route.getOrigin();
        Location to = route.getDestination();

        Route featured = route("Tuyến nổi bật api", to, from);
        featured.setFeatured(true);
        featured.setImageUrl("https://cdn.test/noi-bat.jpg");
        featured = routeRepository.save(featured);

        Trip sold = trip(route, LocalDateTime.now().plusDays(3), TripStatus.ACTIVE, "500000");
        holdAndPay(sold);
        trip(route, LocalDateTime.now().plusDays(4), TripStatus.ACTIVE, "300000");
        // Chuyến đã huỷ và chuyến đã chạy rẻ hơn nhưng không được tính vào minPrice.
        trip(route, LocalDateTime.now().plusDays(5), TripStatus.CANCELLED, "100000");
        trip(route, LocalDateTime.now().minusDays(5), TripStatus.ACTIVE, "90000");

        // Tuyến không nổi bật, không bán được vé thì không được lên danh sách.
        routeRepository.save(route("Tuyến ế api", from, to));

        mockMvc.perform(get("/api/routes/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(featured.getId()))
                .andExpect(jsonPath("$[0].featured").value(true))
                .andExpect(jsonPath("$[0].imageUrl").value("https://cdn.test/noi-bat.jpg"))
                .andExpect(jsonPath("$[0].ticketsSold").value(0))
                .andExpect(jsonPath("$[0].minPrice", nullValue()))
                .andExpect(jsonPath("$[1].id").value(route.getId()))
                .andExpect(jsonPath("$[1].ticketsSold").value(1))
                .andExpect(jsonPath("$[1].minPrice").value(300000.0));

        mockMvc.perform(get("/api/routes/popular").param("limit", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("[4.3] /api/routes/{id} vẫn hoạt động, không bị /popular che")
    void routeByIdStillWorks() throws Exception {
        mockMvc.perform(get("/api/routes/{id}", route.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tuyến api chính"));
    }

    @Test
    @DisplayName("[L4] Swagger liệt kê STRIPE và REFUNDED cho thanh toán")
    void swaggerDocumentsPaymentValues() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.gateway.enum", hasItem("STRIPE")))
                .andExpect(jsonPath("$.components.schemas.PaymentResponse.properties.gateway.enum", hasItem("STRIPE")))
                .andExpect(jsonPath("$.components.schemas.PaymentResponse.properties.status.enum", hasItem("REFUNDED")));
    }

    // ---- Dựng dữ liệu ----

    private Integer postForId(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).get("id").asInt();
    }

    private Trip trip(LocalDateTime departure, TripStatus status) {
        return trip(route, departure, status, "500000");
    }

    private Trip trip(Route tripRoute, LocalDateTime departure, TripStatus status, String price) {
        Trip trip = new Trip();
        trip.setRoute(tripRoute);
        trip.setCompany(company);
        trip.setBusType(BusType.LIMOUSINE_29);
        trip.setDepartureTime(departure);
        trip.setArrivalTime(departure.plusHours(8));
        trip.setBasePrice(new BigDecimal(price));
        trip.setStatus(status);
        trip = tripRepository.saveAndFlush(trip);
        tripSeatRepository.saveAll(seatGenerator.generate(trip));
        return trip;
    }

    private void holdAndPay(Trip trip) {
        Integer seatId = tripSeatRepository.findByTripIdOrderByFloorAscSeatNameAsc(trip.getId()).getFirst().getId();
        Integer booking = bookingService.holdSeats(USER, new HoldSeatsRequest(trip.getId(), List.of(seatId), null, null,
                "Khách kiểm thử", "0901234567", "khach@example.com")).id();
        bookingService.pay(USER, booking, new PaymentRequest("VNPAY", null, null, null));
    }

    private Route route(String name, Location from, Location to) {
        Route newRoute = new Route();
        newRoute.setName(name);
        newRoute.setOrigin(from);
        newRoute.setDestination(to);
        return routeRepository.save(newRoute);
    }

    private Location location(String name, String slug) {
        Location location = new Location();
        location.setName(name);
        location.setSlug(slug);
        return location;
    }
}
