package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.StopPointRequest;
import com.ridebooking.booking.dto.StopPointResponse;
import com.ridebooking.booking.entity.StopPoint;
import com.ridebooking.booking.entity.Trip;
import com.ridebooking.booking.enums.PointType;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.StopPointRepository;
import com.ridebooking.booking.repository.TripRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điểm dừng của một chuyến — phần "Xe đi qua những điểm này" trên Trang chi tiết.
 *
 * <p>Thứ tự điểm dừng do service cấp, không nhận từ request. Nếu nhận từ request
 * thì một lần bấm nhầm là có hai điểm cùng "lần 1", và UI in ra "đón lần 1" hai
 * lần — lỗi nhỏ nhưng khách lo xe vì thấy xe đi vòng.
 */
@Service
@Transactional
public class StopPointService {

    /** Điểm đón xếp trước điểm trả, rồi mỗi nhóm theo thứ tự dọc tuyến. */
    private static final Comparator<StopPoint> DISPLAY_ORDER =
            Comparator.comparing((StopPoint p) -> p.getPointType() == PointType.PICKUP ? 0 : 1)
                    .thenComparing(StopPoint::getStopOrder);

    private final StopPointRepository stopPointRepository;
    private final TripRepository tripRepository;

    public StopPointService(StopPointRepository stopPointRepository, TripRepository tripRepository) {
        this.stopPointRepository = stopPointRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public List<StopPointResponse> listByTrip(Integer tripId) {
        requireTrip(tripId);
        return stopPointRepository.findByTripIdOrderByStopOrderAsc(tripId).stream()
                .sorted(DISPLAY_ORDER)
                .map(StopPointResponse::from)
                .toList();
    }

    /** Thêm điểm dừng vào cuối danh sách cùng loại. */
    public StopPointResponse create(Integer tripId, StopPointRequest request) {
        requireEditableTrip(tripId);
        Trip trip = requireTrip(tripId);
        PointType pointType = parsePointType(request.pointType());

        StopPoint point = new StopPoint();
        point.setTrip(trip);
        point.setName(request.name().trim());
        point.setAddress(blankToNull(request.address()));
        point.setPointType(pointType);
        point.setEstimatedTime(request.estimatedTime());
        point.setStopOrder(nextStopOrder(tripId, pointType));

        return StopPointResponse.from(stopPointRepository.save(point));
    }

    /**
     * Sửa điểm dừng.
     *
     * <p>Đổi {@code pointType} là đổi nhóm, nên thứ tự phải được cấp lại — nếu giữ
     * số cũ, điểm mới có thể nhảy về vị trí 3 giữa những điểm đang đứng ở 2 và 3.
     */
    public StopPointResponse update(Integer stopPointId, StopPointRequest request) {
        StopPoint point = requirePoint(stopPointId);
        requireEditableTrip(point.getTrip().getId());
        PointType newType = parsePointType(request.pointType());
        PointType oldType = point.getPointType();
        boolean typeChanged = oldType != newType;

        point.setName(request.name().trim());
        point.setAddress(blankToNull(request.address()));
        point.setPointType(newType);
        point.setEstimatedTime(request.estimatedTime());

        if (typeChanged) {
            point.setStopOrder(nextStopOrder(point.getTrip().getId(), newType));
            stopPointRepository.save(point);
            // Phải đánh lại CẢ nhóm cũ. Điểm vừa rời nhóm cũ để lấy một chỗ trống,
            // nếu chỉ renumber nhóm mới thì nhóm cũ hở số thứ tự (1, 3, 4...).
            renumber(point.getTrip().getId(), oldType);
            renumber(point.getTrip().getId(), newType);
        }
        return StopPointResponse.from(point);
    }

    /**
     * Xoá điểm dừng rồi đánh lại số thứ tự.
     *
     * <p>Cố tình KHÔNG chặn trên chuyến đã huỷ: xoá là dọn dẹp, Admin vẫn cần xoá
     * được điểm sai trong một chuyến đã huỷ để dữ liệu sạch.
     *
     * <p>Không đánh lại thì danh sách còn lại bị nhảy số (1, 3, 4) và UI hiện
     * "đón lần 3" trong khi thực tế là lần thứ hai.
     */
    public void delete(Integer stopPointId) {
        StopPoint point = requirePoint(stopPointId);
        Integer tripId = point.getTrip().getId();
        PointType pointType = point.getPointType();

        stopPointRepository.delete(point);
        stopPointRepository.flush();
        renumber(tripId, pointType);
    }

    /**
     * Kiểm tra điểm dừng có thuộc đúng chuyến trong đường dẫn không.
     *
     * <p>Không kiểm tra thì URL {@code /api/admin/trips/7/stop-points/3} vẫn sửa
     * được điểm dừng của chuyến 9: Admin nhìn thấy 404 ở một chỗ và lại thấy thay
     * đổi ở chỗ khác, rất khó đoán.
     *
     * <p>Báo 404 chứ không báo 409: theo góc nhìn của URL này, không tồn tại điểm
     * dừng id 3 thuộc chuyến 7.
     */
    public void requireBelongsToTrip(Integer stopPointId, Integer tripId) {
        StopPoint point = requirePoint(stopPointId);
        Integer actualTripId = point.getTrip().getId();
        if (!actualTripId.equals(tripId)) {
            throw new ResourceNotFoundException("Điểm dừng " + stopPointId
                    + " không thuộc chuyến " + tripId + " (nó thuộc chuyến " + actualTripId + ")");
        }
    }

    /** Đánh lại thứ tự liên tục từ 1 cho toàn bộ điểm cùng loại. */
    private void renumber(Integer tripId, PointType pointType) {
        List<StopPoint> remaining =
                stopPointRepository.findByTripIdAndPointTypeOrderByStopOrderAsc(tripId, pointType);
        int order = 1;
        List<StopPoint> changed = new ArrayList<>();
        for (StopPoint point : remaining) {
            if (!point.getStopOrder().equals(order)) {
                point.setStopOrder(order);
                changed.add(point);
            }
            order++;
        }
        stopPointRepository.saveAll(changed);
    }

    private int nextStopOrder(Integer tripId, PointType pointType) {
        return stopPointRepository.findMaxStopOrder(tripId, pointType) + 1;
    }

    private Trip requireTrip(Integer tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe với id " + tripId));
    }

    private StopPoint requirePoint(Integer stopPointId) {
        return stopPointRepository.findById(stopPointId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm dừng với id " + stopPointId));
    }

    static PointType parsePointType(String raw) {
        for (PointType value : PointType.values()) {
            if (value.name().equalsIgnoreCase(raw == null ? "" : raw.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Loại điểm dừng '" + raw + "' không hợp lệ. Chỉ nhận: PICKUP, DROPOFF");
    }

    /** Chuỗi trống lưu thành NULL để không phải xử lý "" và null hai kiểu. */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Chặn việc thêm điểm dừng vào chuyến đã kết thúc hoặc đã huỷ. */
    public void requireEditableTrip(Integer tripId) {
        Trip trip = requireTrip(tripId);
        if (trip.getStatus() != com.ridebooking.booking.enums.TripStatus.ACTIVE) {
            throw new ConflictException("Chuyến đang ở trạng thái " + trip.getStatus()
                    + ", không thêm hoặc sửa điểm dừng");
        }
    }
}