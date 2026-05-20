# Smart City Traffic Simulation

Ứng dụng mô phỏng giao thông đô thị xây dựng bằng **JavaFX 25** + **Maven**.

---

## Chạy ứng dụng

```bash
mvn javafx:run
```

Hoặc compile rồi chạy:
```bash
mvn package
java --module-path target/dependency --add-modules javafx.controls,javafx.media -jar target/smart-city-traffic-1.0.0.jar
```

---

## Cấu trúc thư mục

```
src/main/java/com/trafficsim/
│
├── config/
│   └── SimConfig.java          ← ⭐ ĐIỀU CHỈNH hành vi tại đây
│
├── model/
│   ├── Direction.java
│   ├── TrafficLight.java       ← 3 kiểu đèn (luôn đếm / không / cuối 10s)
│   ├── SimScene.java           ← "Nguồn sự thật" dữ liệu
│   │
│   ├── vehicle/
│   │   ├── Vehicle.java        ← Lớp cơ sở (KHÔNG sửa để thêm xe)
│   │   ├── Car.java
│   │   ├── Motorbike.java
│   │   ├── Bicycle.java
│   │   ├── Bus.java
│   │   ├── Ambulance.java      ← Xe ưu tiên + đèn nháy
│   │   ├── FireTruck.java      ← Xe ưu tiên + đèn nháy
│   │   └── VehicleFactory.java
│   │
│   ├── driver/                 ← Strategy Pattern "bộ não" lái xe
│   │   ├── DrivingBehavior.java (interface)
│   │   ├── NormalDriver.java
│   │   ├── AggressiveDriver.java
│   │   ├── CautiousDriver.java
│   │   └── EmergencyDriver.java
│   │
│   ├── road/
│   │   ├── Road.java
│   │   └── Lane.java
│   │
│   └── intersection/
│       ├── Intersection.java   ← Lớp cơ sở (KHÔNG sửa để thêm ngã tư)
│       ├── ThreeWayIntersection.java
│       ├── FourWayIntersection.java
│       └── FiveWayIntersection.java
│
├── controller/
│   └── TrafficController.java  ← ⭐ KHÔNG biết loại xe cụ thể
│
├── service/
│   ├── SceneBuilder.java       ← Xây dựng các cảnh mô phỏng
│   ├── SpawnService.java       ← Sinh xe ngẫu nhiên
│   └── SoundService.java       ← Âm thanh (graceful fallback)
│
├── view/
│   ├── renderer/
│   │   ├── SceneRenderer.java  ← Interface tách logic vẽ
│   │   ├── BasicRenderer.java  ← Chế độ hình chữ nhật
│   │   └── GraphicRenderer.java← Chế độ sprite ảnh
│   │
│   └── ui/
│       ├── MainWindow.java
│       ├── SimulationCanvas.java
│       └── ControlPanel.java
│
└── TrafficSimApp.java
```

---

## Hướng dẫn mở rộng

### Thêm loại xe mới (ví dụ: Taxi)
```java
// 1. Tạo file Taxi.java kế thừa Vehicle
public class Taxi extends Vehicle {
    public Taxi(double x, double y, Direction dir) {
        super(x, y, dir, 85, 17, 8, new NormalDriver());
    }
    @Override public String getShortName()  { return "Taxi"; }
    @Override public String getColor()      { return "#FFEE00"; }
    @Override public String getSpritePath() { return "/images/vehicles/taxi.png"; }
}

// 2. Thêm vào VehicleFactory.Type và switch statement
// KHÔNG cần sửa TrafficController!
```

### Thêm kiểu lái xe mới
```java
// Implement DrivingBehavior interface là xong
public class RecklessDriver implements DrivingBehavior { ... }
```

### Thêm loại ngã tư mới
```java
// Kế thừa Intersection
public class Roundabout extends Intersection { ... }
// Thêm vào SceneBuilder
```

### Tinh chỉnh hành vi
Sửa các hằng số trong `SimConfig.java`:
- `SPAWN_INTERVAL_*` – tần suất sinh xe
- `DEFAULT_MAX_SPEED` – tốc độ tối đa
- `PRIORITY_YIELD_RANGE` – bán kính nhường đường
- `GREEN_DURATION`, `RED_DURATION` – thời gian đèn

---

## Tính năng

| Tính năng | Chi tiết |
|-----------|----------|
| **5 loại phương tiện** | Ô tô, Xe máy, Xe đạp, Xe buýt, Cứu thương, Cứu hỏa |
| **4 kiểu "bộ não" lái** | Normal, Aggressive, Cautious, Emergency |
| **4 loại cảnh** | Ngã ba, Ngã tư, Ngã năm, Mạng lưới |
| **3 kiểu đèn** | Luôn đếm, Không đếm, Đếm khi ≤10s |
| **2 chế độ đèn** | Tự động / Thủ công (click) |
| **2 chế độ vẽ** | Basic (hình vuông) / Đồ họa (sprite) |
| **Lưu lượng** | Ít / Vừa / Đông đúc |
| **Nhường đường** | Xe thường tự giảm tốc khi xe ưu tiên đến gần |
| **Scale theo cảnh** | Ngã rẽ đơn = ảnh to hơn; Mạng lưới = ảnh nhỏ |
| **Âm thanh** | Còi, xi-nhan, còi hú (tự tắt nếu thiếu file) |

---

## Thêm âm thanh
Đặt file WAV vào `src/main/resources/sounds/`:
- `horn.wav` – còi xe thường
- `siren.wav` – còi xe ưu tiên
- `signal.wav` – xi-nhan
- `engine.wav` – tiếng động cơ

## Thêm sprite xe
Đặt PNG vào `src/main/resources/images/vehicles/`:
- `car.png`, `motorbike.png`, `bicycle.png`, `bus.png`
- `ambulance.png`, `firetruck.png`
