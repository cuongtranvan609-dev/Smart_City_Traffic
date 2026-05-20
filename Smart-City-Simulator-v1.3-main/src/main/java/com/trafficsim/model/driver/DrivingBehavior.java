package com.trafficsim.model.driver;

import com.trafficsim.model.vehicle.Vehicle;
import com.trafficsim.model.road.Lane;

/**
 * Giao diện "bộ não" lái xe – Strategy Pattern.
 * Thêm kiểu lái mới chỉ cần implement interface này.
 */
public interface DrivingBehavior {

    /**
     * Tính toán gia tốc mong muốn cho bước thời gian hiện tại.
     *
     * @param self       phương tiện đang xét
     * @param lane       làn đường hiện tại
     * @param frontVehicle xe phía trước (null nếu trống)
     * @param dt         delta time (giây)
     * @return gia tốc (px/s²), âm = giảm tốc
     */
    double computeAcceleration(Vehicle self, Lane lane, Vehicle frontVehicle, double dt);

    /**
     * Quyết định xem xe có dừng tại vạch đèn đỏ không.
     */
    boolean shouldStopAtRedLight(Vehicle self, Lane lane);

    /**
     * Quyết định xem xe có vượt lên không khi có khoảng trống.
     */
    boolean shouldOvertake(Vehicle self, Vehicle frontVehicle, Lane adjacentLane);

    /** Tên hiển thị của hành vi lái */
    String getName();
}
