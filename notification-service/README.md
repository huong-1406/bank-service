# notification-service

> Nghe hàng đợi **`payment.queue`** của ActiveMQ. Mỗi message là một thanh toán thành công → lưu lại và thông báo cho khách.
> Đề bài yêu cầu **giả lập** việc gửi thông báo bằng cách log ra console: `Payment confirmed for paymentId: 12345`.

| | |
|---|---|
| Port | **8082** (chỉ có `/actuator/health`, không có API nghiệp vụ) |
| Database | `notification_db` — container `postgres-notification`, port máy **5434** |
| Nhận từ | ActiveMQ, hàng đợi `payment.queue` (do payment-service gửi) |
| Gửi tới | Log console |

Luồng thanh toán đầy đủ: `../ARCHITECTURE2.md` §7.

---

## 1. Cấu trúc thư mục

```
src/main/java/com/bank/notificationservice/
├── messaging/PaymentListener.java              ⭐ @JmsListener nghe payment.queue — thay cho Controller
├── messaging/PaymentMessage.java               nội dung message nhận được (4 trường)
├── service/NotificationService.java            interface
│   └── impl/NotificationServiceImpl.java       ⭐ chống trùng → lưu → log
├── repository/NotificationRepository.java      lưu, tìm bảng notification
├── entity/Notification.java                    bảng notification
└── config/JmsConfig.java                       đọc message JSON (_type = payment)
```

Không có Controller vì service này không nhận HTTP — đầu vào là `PaymentListener`.

---

## 2. Bảng `notification`

| Cột | Ghi chú |
|---|---|
| `id` | Khoá chính |
| `payment_id` | **UNIQUE** — chống xử lý trùng message. Không phải khoá ngoại vì khác database |
| `account_id`, `amount`, `currency` | Lấy từ message |
| `message` | Câu đã log, ví dụ `Payment confirmed for paymentId: 44` |
| `created_at` | |

---

## 3. Luồng xử lý

```
payment.queue ──► JmsConfig (JSON → PaymentMessage)
                     ▼
              PaymentListener.onPaymentMessage()
                     ▼
              NotificationServiceImpl.notifyPaymentConfirmed()
```

`NotificationServiceImpl`:
1. **Chống trùng:** `existsByPaymentId(paymentId)` — có rồi thì bỏ qua, không log lần 2.
2. **Lưu** bảng `notification`.
3. **Log:** `Payment confirmed for paymentId: 44`

### Vì sao phải chống trùng
ActiveMQ chỉ xoá message khi notification-service báo đã xử lý xong. Nếu service sập giữa chừng, ActiveMQ **giao lại** cùng message. Chặn ở 2 chỗ:
- Code: `existsByPaymentId`
- Database: cột `payment_id` UNIQUE

---

## 4. Khi notification-service tắt

- payment-service vẫn gửi được message → **thanh toán vẫn thành công**.
- Message **nằm chờ** trong `payment.queue` (trang ActiveMQ: Pending tăng, Consumers = 0).
- Bật lại → tự kết nối, nhận hết message đang chờ, log từng thông báo.

---

## 5. Chạy và kiểm tra

```bash
# Dựng lại riêng notification-service
docker compose up -d --build notification-service

# Xem các thông báo đã log
docker logs notification-service | grep "Payment confirmed"

# Chạy unit test (2 test)
./mvnw -pl notification-service -am test
```

Thử message nằm chờ:
```bash
docker stop notification-service     # rồi thanh toán vài lần → Pending tăng
docker start notification-service    # tự xử lý hết, Pending về 0
```
