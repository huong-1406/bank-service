# Kế hoạch 1 tuần — làm đủ, không cắt

> Kiến trúc: `ARCHITECTURE2.md` · Database: `DATABASE.md` · API: `API.md`. File này là **lịch làm việc**.
> Phạm vi: giữ nguyên 100% đề bài, kể cả 10 màn frontend.

---

## 1. Khối lượng thật

| Hạng mục | Ước lượng |
|---|---|
| 12 API + ràng buộc + exception handler | 12h |
| JWT + Security | 6h |
| Redis cache | 4h |
| ActiveMQ — 2 service phụ | 6h |
| Docker — 4 image | 4h |
| Unit test — 2 test × 12 API | 6h |
| Postman — 24 case | 3h |
| Frontend React — 10 màn | 20h |
| **Tổng** | **~61h** |

- Làm **7 ngày** → ~9h/ngày
- Làm **5 ngày** → ~12h/ngày

Không cắt gì thì con số là vậy. Lịch dưới đây chia theo 7 ngày.

---

## 2. Đòn bẩy quan trọng nhất: đảo thứ tự JWT

Đề bài xếp JWT ở **ngày 4-5**, sau khi đã viết xong 10 API. Làm theo thứ tự đó thì tới ngày 4 phải **sửa lại toàn bộ 10 API** để bỏ `accountId` khỏi URL, rồi sửa tiếp Postman và frontend đã trót nối.

Làm JWT **ngay ngày 1** thì không bao giờ phải sửa lại — API viết ra đã là `/accounts/me` ngay từ đầu.

**Tiết kiệm được 5-6 tiếng.** Với 1 tuần thì đây là khác biệt giữa kịp và không kịp.

> Vẫn đúng yêu cầu đề bài. Đề bài yêu cầu *kết quả* là accountId lấy từ JWT, không quy định thứ tự làm.

---

## 3. Ba nguyên tắc chạy song song

**Frontend bắt đầu từ ngày 1, dùng dữ liệu giả.** Không chờ backend. Ngày 4 mới nối API thật.

**Viết test ngay sau mỗi service.** Đừng dồn tới cuối tuần — lúc đó vừa mệt vừa quên logic vừa viết.

**Viết Postman ngay sau mỗi API.** Vì JWT đã xong từ ngày 1 nên không phải sửa lại.

---

## 4. Lịch 7 ngày

| Ngày | Backend | Frontend |
|---|---|---|
| **1** | Khung + entity + **JWT + login** | Dựng Vite, layout, màn login |
| **2** | 4 API tài khoản + exception handler | Dashboard, màn sửa thông tin |
| **3** | 6 API thẻ và số dư | Màn thẻ, màn nạp/rút |
| **4** | Redis cache | **Nối API thật** cho các màn đã có |
| **5** | ActiveMQ — 2 service phụ | Màn thanh toán, xử lý lỗi |
| **6** | Docker — 4 image | 10 màn xong hết, chạy trong Docker |
| **7** | Unit test bù + Postman + rà soát | Sửa nốt, chuẩn bị nộp |

---

## 5. Chi tiết từng ngày

### Ngày 1 — Khung + JWT

**Sáng:** multi-module, `git init` + push GitLab, `docker compose up -d` hạ tầng.

Rồi **thiết kế database** trước khi gõ entity (chi tiết `ARCHITECTURE2.md` §4.3–4.10):

| Quyết định | Chọn |
|---|---|
| Tiền | `DECIMAL(19,2)` + `BigDecimal` — **không dùng** `double` |
| Enum | Lưu chuỗi, không lưu số |
| `balance.account_id` | Vừa PK vừa FK → ép quan hệ 1–1 |
| `card.account_id` | `ON DELETE RESTRICT` để DB tự chặn xoá tài khoản còn thẻ |
| Chống rút tiền trùng | Thêm cột `version` vào `balance` |

Xong mới viết 4 entity + 4 repository + file `schema-extra.sql` chứa 3 index và các `CHECK`.

> Thiết kế đầy đủ — cột, ràng buộc, index, dữ liệu mẫu — xem `DATABASE.md`.

**Chiều:** `SecurityConfig`, `JwtUtil`, filter, `POST /auth/login`, `POST /accounts` (đăng ký). Hai endpoint này `permitAll`.

Song song: `npm create vite`, cài router + axios + MUI, dựng layout, làm màn login.

> Bảng `Transaction` đề bài không liệt kê nhưng **bắt buộc phải có** — cần nó để chặn xoá thẻ.

### Ngày 2 — Account

| API | Ràng buộc |
|---|---|
| `GET /accounts/me` | Trả kèm số dư |
| `PUT /accounts/me` | Chỉ sửa email + SĐT |
| `DELETE /accounts/me` | Chặn nếu còn thẻ **hoặc** số dư ≠ 0 |

Thêm `GlobalExceptionHandler` (404 / 400) và unit test cho `AccountServiceImpl`.

### Ngày 3 — Card + Balance

| API | Ràng buộc |
|---|---|
| `GET /accounts/me/cards` | |
| `POST /accounts/me/cards` | Chặn nếu tài khoản không tồn tại |
| `DELETE /cards/{id}` | Chặn nếu thẻ còn giao dịch `PENDING` |
| `GET /accounts/me/balance` | |
| `POST /balance/deposit` | Số tiền > 0 |
| `POST /balance/withdraw` | Chặn nếu số dư không đủ + thẻ phải `ACTIVE` |

Unit test cho `CardServiceImpl` và `BalanceServiceImpl` ngay trong ngày.

### Ngày 4 — Redis

Cache chi tiết tài khoản và số dư, TTL 10 phút. Nạp/rút cập nhật cache ngay, không chờ hết hạn.

> Nhớ xoá cache khi sửa hoặc xoá tài khoản — quên là trả dữ liệu cũ suốt 10 phút.

Frontend hôm nay **nối API thật**. Bật CORS trong `SecurityConfig`, cho `OPTIONS` đi qua không cần token.

### Ngày 5 — ActiveMQ

Dựng `payment-service` (~6 file) và `notification-service` (~4 file). Cả hai rất mỏng, không DB, không JWT.

Luồng: `bank-service` ghi `Transaction` PENDING → khoá tiền vào `holdBalance` → gọi HTTP sang `payment-service` → đẩy message vào queue → `notification-service` log `"Payment confirmed for paymentId: ..."`.

Listener phải bỏ qua message trùng, vì ActiveMQ có thể gửi lại.

### Ngày 6 — Docker

4 Dockerfile: 3 backend (maven build → JRE) + 1 frontend (node build → nginx). Thêm vào compose, `docker compose up --build`.

| Chỗ hay sập | Cách tránh |
|---|---|
| `localhost` trong config | Đổi sang tên container |
| F5 trang con bị 404 | `try_files $uri /index.html` trong nginx |
| Frontend gọi sai địa chỉ API | `VITE_API_BASE_URL` truyền qua **build arg**, không phải `environment` |

### Ngày 7 — Hoàn thiện

Bù nốt unit test cho đủ 2 test mỗi API. Hoàn thiện Postman collection. Chạy lại toàn bộ trong Docker. Rà checklist mục 7.

---

## 6. Ba lỗi làm mất nhiều thời gian nhất

| Lỗi | Hậu quả | Tránh bằng |
|---|---|---|
| Làm JWT muộn | Sửa lại 10 API + Postman + frontend | Làm ngay ngày 1 (mục 2) |
| Để `localhost` trong config tới ngày 6 | Debug Docker rất lâu | Dùng biến môi trường từ ngày 1 |
| Nhét logic vào Controller | Mất điểm phần chấm chính | Controller chỉ nhận/trả, không `if` nghiệp vụ |

---

## 7. Checklist trước khi nộp

- [ ] Tiền lưu `DECIMAL`, không chỗ nào dùng `double`
- [ ] Có 3 index, nhất là `transaction(card_id, status)`
- [ ] `balance` có cột `version` chống rút tiền trùng
- [ ] 12 API công khai chạy đúng
- [ ] 3 ràng buộc nghiệp vụ đều chặn được
- [ ] Không API nào nhận `accountId` từ body/param
- [ ] Redis có cache, TTL 10 phút
- [ ] Message đi qua ActiveMQ, thấy log ở notification-service
- [ ] `docker compose up` chạy trọn bộ 7 container
- [ ] 2 unit test mỗi API (1 pass + 1 fail)
- [ ] Đặc tả API khớp thực tế (`API.md`)
- [ ] Postman collection đủ case pass và fail
- [ ] Frontend đủ 10 màn
- [ ] GitLab có commit theo từng chức năng
