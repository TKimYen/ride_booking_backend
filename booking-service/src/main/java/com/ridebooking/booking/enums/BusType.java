package com.ridebooking.booking.enums;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Loại xe — quyết định SƠ ĐỒ GHẾ của một chuyến.
 *
 * <p>Giá trị {@code name()} phải khớp với CHECK constraint trong
 * {@code database/03_booking_db.sql} (mục ck_trips_bus_type). Thêm loại xe mới
 * thì sửa cả hai nơi, nếu không sẽ có chuyến tạo được trong code nhưng bị DB
 * từ chối.
 *
 * <p>Sơ đồ ghế mô tả theo kiểu limousine phổ biến ở Việt Nam: tầng dưới có
 * ghế đôi, tầng trên có ghế đôi và ghế đơn. Số ghế mỗi loại được chốt trong
 * hằng {@link #seatCount()} và được {@code BookingServiceApplication} kiểm tra
 * lúc khởi động — xem {@link #verifyLayoutsAreConsistent()}.
 */
public enum BusType {

    /**
     * Limousine 29 chỗ, 2 tầng.
     * Tầng dưới: 5 hàng ghế đôi 2x2 + 1 ghế đơn = 11 chỗ.
     * Tầng trên: 9 hàng ghế đôi 2x2 = 18 chỗ. Tổng 29.
     */
    LIMOUSINE_29("Limousine 29 chỗ", 29, 2),

    /**
     * Cabin đôi 22 phòng, 2 tầng. Mỗi phòng 2 ghế tách ngăn,
     * 11 phòng mỗi tầng nên 44 chỗ ngồi. Con số 22 trong tên là SỐ PHÒNG,
     * không phải số chỗ.
     */
    CABIN_DOUBLE_22("Cabin đôi 22 phòng", 44, 2),

    /** Giường nằm 45 chỗ, 1 tầng, 15 hàng x 3 ghế. */
    SLEEPER_45("Giường nằm 45 chỗ", 45, 1);

    private final String displayName;
    private final int seatCount;
    private final int floors;

    BusType(String displayName, int seatCount, int floors) {
        this.displayName = displayName;
        this.seatCount = seatCount;
        this.floors = floors;
    }

    public String displayName() {
        return displayName;
    }

    /** Số ghế thực tế của một chuyến loại này. */
    public int seatCount() {
        return seatCount;
    }

    public int floors() {
        return floors;
    }

    /** Loại ghế dùng khi sinh ghế. Cùng giá trị với CHECK ở bảng {@code trip_seats}. */
    public static final class SeatType {

        public static final String STANDARD = "STANDARD";
        public static final String DOUBLE = "DOUBLE";
        public static final String SINGLE = "SINGLE";

        private SeatType() {
        }
    }

    /**
 * Phần cộng thêm cho MỘT ghế thuộc hàng ghế đôi.
 *
 * <p>Ghế đắt vì khách ngồi cạnh nhau được chọn cùng nhau, nên hàng ghế đôi định
 * giá cao hơn ghế đơn.
 */
public static final BigDecimal DOUBLE_SURCHARGE = new BigDecimal("250000");

    /**
     * Ghế SÁT LỐI ĐI đắt hơn ghế sát cửa sổ.
     *
     * <p>Đây là phần cộng THÊM, không phải tổng giá của ghế sát lối đi. Trước đây
     * nó được đặt tên là {@code DOUBLE_CENTER_SURCHARGE} và bằng 50.000 — nhỏ hơn
     * chính {@link #DOUBLE_SURCHARGE}, nên ghế sát lối đi lại RẺ hơn ghế sát cửa
     * sổ, đúng ngược với giá thị trường và dễ làm Admin tin nhầm khi xem báo giá.
     */
    public static final BigDecimal DOUBLE_AISLE_EXTRA = new BigDecimal("50000");

    /**
     * Tên ghế tạo ra cho một chuyến loại này.
     *
     * <p>Tính ở đây chứ không lưu cứng trong DB để thêm loại xe mới không phải
     * sửa SQL. Thứ tự trả về là tầng 1 trước, trong mỗi tầng là hàng từ trước
     * ra sau, mỗi hàng từ trái sang phải — đúng thứ tự frontend vẽ sơ đồ ghế.
     */
    public List<SeatSpec> layout() {
        return switch (this) {
            case LIMOUSINE_29 -> limousine29();
            case CABIN_DOUBLE_22 -> cabinDouble22();
            case SLEEPER_45 -> sleeper45();
        };
    }

    /** Tầng 1: 5 hàng ghế đôi + 1 ghế đơn = 11. Tầng 2: 9 hàng ghế đôi = 18. */
    private List<SeatSpec> limousine29() {
        List<SeatSpec> seats = new ArrayList<>();

        for (int row = 1; row <= 5; row++) {
            addDoubleRow(seats, 1, rowLetter(row), 2);
        }
        seats.add(new SeatSpec(seatLabel(1, "FL"), 1, SeatType.SINGLE, BigDecimal.ZERO));

        for (int row = 1; row <= 9; row++) {
            addDoubleRow(seats, 2, rowLetter(row), 2);
        }

        return seats;
    }

    /** 11 phòng mỗi tầng, mỗi phòng 2 ghế. */
    private List<SeatSpec> cabinDouble22() {
        List<SeatSpec> seats = new ArrayList<>();
        for (int floor = 1; floor <= 2; floor++) {
            for (int row = 1; row <= 11; row++) {
                seats.add(new SeatSpec(seatLabel(floor, rowLetter(row) + "1"),
                        floor, SeatType.DOUBLE, DOUBLE_SURCHARGE));
                seats.add(new SeatSpec(seatLabel(floor, rowLetter(row) + "2"),
                        floor, SeatType.DOUBLE, DOUBLE_SURCHARGE));
            }
        }
        return seats;
    }

    /** 15 hàng x 3 ghế, 1 tầng. */
    private List<SeatSpec> sleeper45() {
        List<SeatSpec> seats = new ArrayList<>();
        for (int row = 1; row <= 15; row++) {
            String letter = rowLetter(row);
            seats.add(new SeatSpec(seatLabel(1, letter + "1"), 1, SeatType.STANDARD, BigDecimal.ZERO));
            seats.add(new SeatSpec(seatLabel(1, letter + "2"), 1, SeatType.STANDARD, BigDecimal.ZERO));
            seats.add(new SeatSpec(seatLabel(1, letter + "3"), 1, SeatType.STANDARD, BigDecimal.ZERO));
        }
        return seats;
    }

    /**
     * Ghép tên ghế duy nhất trong cả chuyến.
     *
     * <p>Xe hai tầng ghi kèm số tầng vào tên: {@code 1A1} ở tầng dưới, {@code 2A1}
     * ở tầng trên. Không làm vậy thì hai ghế cùng tên, và với khoá duy nhất
     * {@code uk_trip_seats_trip_name (trip_id, seat_name)} lần chuyến thứ hai sẽ
     * không tạo được — tên ghế lặp trong sơ đồ là vô dụng với khách, và ghế tầng
     * trên mất tên.
     *
     * <p>Xe một tầng giữ tên dạng {@code A1} cho quen thuộc; vậy vẫn duy nhất.
     */
    private String seatLabel(int floor, String base) {
        return floors() > 1 ? floor + base : base;
    }

    /**
     * Một hàng ghế đôi: cột 1 sát cửa sổ, cột 2 sát lối đi.
     *
     * <p>Cột sát lối đi cộng thêm {@link #DOUBLE_AISLE_EXTRA} vì ra vào nhanh hơn.
     */
    private void addDoubleRow(List<SeatSpec> seats, int floor, String letter, int columns) {
        for (int column = 1; column <= columns; column++) {
            boolean aisleSeat = columns == 2 && column == 2;
            seats.add(new SeatSpec(seatLabel(floor, letter + column), floor, SeatType.DOUBLE,
                    aisleSeat ? DOUBLE_SURCHARGE.add(DOUBLE_AISLE_EXTRA) : DOUBLE_SURCHARGE));
        }
    }

    /** 1->A, 2->B ... 26->Z, 27->AA. Ghế trên xe không vượt quá 15 hàng. */
    public static String rowLetter(int row) {
        StringBuilder sb = new StringBuilder();
        int n = row;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * Kiểm tra {@link #seatCount()} khớp với số ghế {@link #layout()} thật sự sinh
     * ra, và tên ghế không trùng trong một chuyến.
     *
     * <p>Đã xảy ra một lần: enum tên {@code LIMOUSINE_29} nhưng hàm layout sinh 49
     * ghế. Không có hàm này thì lỗi đó chỉ lộ ra khi khách đặt nhầm chỗ. Nếu
     * trả về danh sách lỗi rỗng thì schema lành lặn.
     */
    public static List<String> verifyLayoutsAreConsistent() {
        List<String> problems = new ArrayList<>();
        for (BusType type : values()) {
            List<SeatSpec> layout = type.layout();

            if (layout.size() != type.seatCount()) {
                problems.add(type + ": tên khai " + type.seatCount()
                        + " chỗ nhưng layout() sinh " + layout.size() + " ghế");
            }

            long distinct = layout.stream().map(SeatSpec::name).distinct().count();
            if (distinct != layout.size()) {
                problems.add(type + ": layout() sinh tên ghế trùng ("
                        + (layout.size() - distinct) + " chỗ)");
            }

            for (SeatSpec seat : layout) {
                if (seat.floor() < 1 || seat.floor() > type.floors()) {
                    problems.add(type + ": ghế " + seat.name() + " nằm ở tầng "
                            + seat.floor() + " vượt quá số tầng " + type.floors());
                }
            }
        }
        return problems;
    }

    /** Một ghế trong sơ đồ, chưa gắn id và trạng thái. */
    public record SeatSpec(String name, int floor, String seatType, BigDecimal extraPrice) {
    }
}