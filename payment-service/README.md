# payment-service

> Nhận yêu cầu thanh toán từ **bank-service** qua HTTP, rồi gửi message vào hàng đợi **`payment.queue`** của ActiveMQ.
> Không kiểm tra số dư hay thẻ — bank-service đã kiểm tra hết trước khi gọi sang.

| | |
|---|---|
| Port | **8081** |
| Database | `payment_db` — container `postgres-payment`, port máy **5433** |
| Nhận từ | bank-service, qua HTTP `POST /payments` |
| Gửi tới | ActiveMQ, hàng đợi `payment.queue` → notification-service |
| Xác thực | Không — API nội bộ, chỉ bank-service gọi |

Luồng thanh toán đầy đủ: `../ARCHITECTURE2.md` §7.

---

## 1. Cấu trúc thư mục

```
src/main/java/com/bank/paymentservice/
├── controller/PaymentController.java      nhận POST /payments, trả 202
├── service/PaymentService.java            interface
│   └── impl/PaymentServiceImpl.java       ⭐ chống trùng → lưu → gửi message
├── repository/PaymentRepository.java      lưu, tìm bảng payment
├── entity/Payment.java                    bảng payment
├── entity/PaymentStatus.java              RECEIVED / SENT
├── dto/PaymentRequest.java                dữ liệu bank-service gửi sang
├── messaging/PaymentMessage.java          nội dung message gửi vào ActiveMQ
└── config/JmsConfig.java                  tên hàng đợi, gửi message dạng JSON
```

---

## 2. API

`POST /payments` — chỉ bank-service gọi.

```json
{ "paymentId": 44, "accountId": 1, "amount": 300000, "currency": "VND" }
```

| Kết quả | Mã |
|---|---|
| Nhận và gửi message thành công | `202` |
| Gửi trùng `paymentId` | `202` — bỏ qua, không gửi message lần 2 |
| Thiếu dữ liệu | `400` |
| ActiveMQ tắt | `500` — không lưu gì, bank-service sẽ hoàn tiền |

`paymentId` chính là mã giao dịch bên `bank_db`.

---

## 3. Bảng `payment`

| Cột | Ghi chú |
|---|---|
| `id` | Khoá chính |
| `transaction_id` | `paymentId` nhận được. **UNIQUE** — chống nhận trùng. Không phải khoá ngoại vì khác database |
| `account_id` | Mã tài khoản bên `bank_db` |
| `amount`, `currency` | Số tiền |
| `status` | `RECEIVED` → `SENT` |
| `created_at` | |

---

## 4. Luồng xử lý — `PaymentServiceImpl.receivePayment`

1. **Chống trùng:** `existsByTransactionId(paymentId)` — có rồi thì dừng.
2. **Lưu** bảng `payment`, trạng thái `RECEIVED`.
3. **Gửi message** vào `payment.queue`:
   ```java
   jmsTemplate.convertAndSend(JmsConfig.PAYMENT_QUEUE, message);
   ```
4. Đổi trạng thái `SENT`.

Cả 4 bước nằm trong **một transaction**: gửi message lỗi thì việc lưu cũng bị huỷ.

Message dạng JSON, kèm thuộc tính `_type = payment` để notification-service biết đổi về class nào:
```json
{"paymentId":44,"accountId":1,"amount":300000,"currency":"VND"}
```

---

## 5. Chạy và kiểm tra

```bash
# Dựng lại riêng payment-service
docker compose up -d --build payment-service

# Xem log
docker logs payment-service

# Chạy unit test (2 test)
./mvnw -pl payment-service -am test
```

Xem hàng đợi: `http://localhost:8161` → đăng nhập `admin / admin` → **Queues** → `payment.queue`.
- **Pending**: số message đang chờ notification-service lấy
- **Consumers**: số bên đang nghe (1 = notification-service đang chạy)
