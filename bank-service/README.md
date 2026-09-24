# bank-service

> Service chính của hệ thống — **toàn bộ nghiệp vụ ngân hàng**: đăng nhập, tài khoản, thẻ, số dư, thanh toán.
> Là service **duy nhất** người dùng gọi trực tiếp. Hai service còn lại (`payment-service`, `notification-service`) chỉ được gọi nội bộ.

| | |
|---|---|
| Port | **8080** |
| Database | `bank_db` — container `postgres-bank`, port máy **5432** |
| Cache | Redis `:6379` |
| Gọi sang | `payment-service :8081` qua HTTP (khi thanh toán) |
| Xác thực | JWT — `accountId` luôn lấy từ token, không bao giờ từ URL hay body |

Tài liệu liên quan: `../API.md` (đặc tả API) · `../DATABASE.md` (thiết kế bảng) · `../ARCHITECTURE2.md` (kiến trúc tổng).

---

## 1. Cấu trúc thư mục

```
src/main/java/com/bank/bankservice/
├── BankServiceApplication.java
│
├── controller/     TẦNG 1 — nhận request, validate định dạng, trả response
├── service/        TẦNG 2 — interface
│   └── impl/                  toàn bộ luật nghiệp vụ của đề bài nằm ở đây
├── repository/     TẦNG 3 — đọc / ghi database
│
├── entity/         4 class ánh xạ 4 bảng
│   └── enums/                 CardType · CardStatus · TransactionType · TransactionStatus
├── dto/
│   ├── request/               JSON gửi lên (kèm quy tắc validate)
│   └── response/              JSON trả về (không bao giờ có password)
├── security/       JwtUtil · JwtAuthenticationFilter · SecurityConfig · SecurityUtil
└── exception/      ErrorCode · BusinessException · ErrorResponse · GlobalExceptionHandler

src/main/resources/
├── application.yml
├── schema-extra.sql   index + CHECK mà Hibernate không tự tạo được
└── data.sql           4 tài khoản mẫu A, B, C, D — mật khẩu Test@1234
```

### Mỗi tầng làm gì

| Tầng | Làm | Không làm |
|---|---|---|
| **Controller** | `@Valid` kiểm tra định dạng · lấy `accountId` bằng `SecurityUtil.currentAccountId()` · gọi Service · chọn mã HTTP | Không kiểm tra luật nghiệp vụ, không gọi Repository |
| **Service** | Kiểm tra **mọi luật đề bài** · vi phạm thì `throw new BusinessException(ErrorCode.XXX)` · mở transaction | Không biết gì về HTTP |
| **Repository** | Truy vấn database | Không chứa logic |

---

## 2. Bảng

4 bảng trong `bank_db`, đúng đề bài §3. Chi tiết cột và ràng buộc ở `../DATABASE.md` §4.

```
account ──1─1── balance         số dư: available (dùng được) + hold (đang giữ)
   │
   ├──1─N── card                DEBIT/CREDIT · ACTIVE/INACTIVE · ngày hết hạn
   │          │
   └──1─N── transaction ─N─0..1─┘  DEPOSIT/WITHDRAW/PAYMENT · PENDING/COMPLETED/FAILED
```

| Bảng | Entity | Repository | Ràng buộc chính |
|---|---|---|---|
| `account` | `Account` | `AccountRepository` | email **UNIQUE**, SĐT **UNIQUE** |
| `balance` | `Balance` | `BalanceRepository` | PK = FK → 1–1 · số dư **≥ 0** · cột `version` chống ghi đè |
| `card` | `Card` | `CardRepository` | FK **RESTRICT** — còn thẻ thì không xoá được tài khoản |
| `transaction` | `Transaction` | `TransactionRepository` | `amount > 0` · rút/thanh toán **bắt buộc có thẻ** |

---

## 3. Chức năng

**12 API** chia 5 nhóm. Cột "Service" là nơi chứa luật nghiệp vụ của API đó.

| # | API | Token | Controller | Service | Trạng thái |
|---|---|---|---|---|---|
| 1 | `POST /auth/login` | ❌ | `AuthController` | `AuthServiceImpl.login` | ✅ |
| 2 | `POST /accounts` | ❌ | `AccountController` | `AccountServiceImpl.createAccount` | ✅ |
| 3 | `GET /accounts/me` | ✅ | `AccountController` | `AccountServiceImpl.getAccount` | ✅ (chưa cache) |
| 4 | `PUT /accounts/me` | ✅ | `AccountController` | `AccountServiceImpl.updateAccount` | ✅ |
| 5 | `DELETE /accounts/me` | ✅ | `AccountController` | `AccountServiceImpl.deleteAccount` | ✅ |
| 6 | `GET /accounts/me/cards` | ✅ | `CardController` | `CardServiceImpl.getCards` | ✅ |
| 7 | `POST /accounts/me/cards` | ✅ | `CardController` | `CardServiceImpl.createCard` | ✅ |
| 8 | `DELETE /cards/{id}` | ✅ | `CardController` | `CardServiceImpl.deleteCard` | ✅ |
| 9 | `GET /accounts/me/balance` | ✅ | `BalanceController` | `BalanceServiceImpl.getBalance` | ✅ (chưa cache) |
| 10 | `POST /balance/deposit` | ✅ | `BalanceController` | `BalanceServiceImpl.deposit` | ✅ |
| 11 | `POST /balance/withdraw` | ✅ | `BalanceController` | `BalanceServiceImpl.withdraw` | ✅ |
| 12 | `POST /payments` | ✅ | `PaymentController` | `PaymentServiceImpl.pay` | ⬜ làm cùng ActiveMQ |

Request / response đầy đủ của từng API: `../API.md` §5.

### 3.1 Đăng nhập

| API | Các bước trong Service | Bảng | Lỗi |
|---|---|---|---|
| **1** Đăng nhập | Tìm theo email → so BCrypt → tạo JWT chứa `accountId`, hạn 1 giờ | R `account` | `401 INVALID_CREDENTIALS` — sai email hay sai mật khẩu đều cùng một lỗi |

### 3.2 Tài khoản

| API | Các bước trong Service | Bảng | Lỗi |
|---|---|---|---|
| **2** Đăng ký | Kiểm tra trùng email, SĐT → hash mật khẩu → lưu `account` → tạo `balance` = 0, **cùng transaction** | R W `account` · W `balance` | `409 EMAIL_ALREADY_EXISTS` · `409 PHONE_ALREADY_EXISTS` · `400 VALIDATION_FAILED` |
| **3** Xem | Đọc tài khoản + số dư | R `account` `balance` | `404 ACCOUNT_NOT_FOUND` |
| **4** Sửa | **Chỉ** email và SĐT (đề bài). Chỉ kiểm tra trùng khi giá trị thật sự đổi | R W `account` | `409` trùng · `400` sai định dạng hoặc body rỗng |
| **5** Xoá | Còn thẻ? → chặn. `available` **hoặc** `hold` ≠ 0? → chặn. Xoá `balance` rồi `account` | R `card` `balance` · W xoá | `400 ACCOUNT_HAS_CARDS` · `400 ACCOUNT_BALANCE_NOT_ZERO` |

### 3.3 Thẻ

| API | Các bước trong Service | Bảng | Lỗi |
|---|---|---|---|
| **6** Danh sách | Lấy thẻ của mình. Không có thì trả `[]`, **không** 404 | R `card` | |
| **7** Tạo | Tài khoản phải tồn tại (đề bài) → tạo thẻ, mặc định `ACTIVE` | R `account` · W `card` | `404 ACCOUNT_NOT_FOUND` · `400` ngày quá khứ |
| **8** Xoá | Tìm theo `id` **và** `accountId` → còn giao dịch `PENDING`? → chặn → xoá | R `card` `transaction` · W xoá `card` | `404 CARD_NOT_FOUND` (kể cả thẻ của người khác) · `400 CARD_HAS_PENDING_TRANSACTION` |

### 3.4 Số dư

| API | Các bước trong Service | Bảng | Lỗi |
|---|---|---|---|
| **9** Xem | `totalBalance` = `available` + `hold`, tính ra, không lưu | R `balance` | |
| **10** Nạp | `available += amount` → ghi `transaction` DEPOSIT, `card = null` | W `balance` `transaction` | `400` amount ≤ 0 · `409 CONCURRENT_UPDATE` |
| **11** Rút | Thẻ của mình? → **thẻ hợp lệ** (`ACTIVE` và chưa hết hạn)? → **`available` ≥ amount**? → trừ tiền → ghi `transaction` WITHDRAW | R `card` · W `balance` `transaction` | `404 CARD_NOT_FOUND` · `400 CARD_NOT_ACTIVE` · `400 INSUFFICIENT_BALANCE` · `409` |

### 3.5 Thanh toán ⬜

| API | Các bước trong Service | Bảng | Lỗi |
|---|---|---|---|
| **12** Thanh toán | Kiểm tra như API 11 → `available −`, `hold +` → ghi `transaction` PAYMENT **PENDING**, commit → gọi `payment-service` → `202` thì **COMPLETED**, `hold −` · lỗi/timeout thì **FAILED**, trả tiền về `available` | R `card` · W `balance` `transaction` | Giống API 11 |

### 3.6 Ba luật đề bài nhắc 3 lần — nằm ở đâu

| Luật | Kiểm tra ở |
|---|---|
| Chỉ thẻ hợp lệ mới được hoạt động | `Card.isValid()` — gọi trong `BalanceServiceImpl.withdraw` (và `pay` sau này) |
| Không tạo thẻ cho tài khoản không tồn tại | `CardServiceImpl.createCard` |
| Không trừ tiền khi số dư khả dụng không đủ | `BalanceServiceImpl.withdraw` (và `pay` sau này) |

---

## 4. Thêm API mới vào đâu

Làm theo thứ tự từ dưới lên — tầng dưới xong thì tầng trên mới có cái để gọi.

| Bước | Việc | File |
|---|---|---|
| 1 | Cần truy vấn mới? Thêm method vào Repository. Đặt tên theo quy ước Spring Data là đủ, ví dụ `existsByAccountId` | `repository/XxxRepository.java` |
| 2 | Tạo DTO request (kèm `@NotNull`, `@Size`...) và DTO response (kèm `static from(entity)`) | `dto/request/`, `dto/response/` |
| 3 | Cần lỗi mới? Thêm vào enum, **và thêm vào bảng mã lỗi `../API.md` §2** | `exception/ErrorCode.java` |
| 4 | Khai báo method trong interface | `service/XxxService.java` |
| 5 | Viết luật nghiệp vụ. Vi phạm thì `throw new BusinessException(ErrorCode.XXX)`. Ghi thì `@Transactional`, chỉ đọc thì `@Transactional(readOnly = true)` | `service/impl/XxxServiceImpl.java` |
| 6 | Thêm endpoint: `@Valid @RequestBody`, `SecurityUtil.currentAccountId()`, `@ResponseStatus` nếu không phải 200 | `controller/XxxController.java` |
| 7 | API không cần token? Thêm vào `permitAll` | `security/SecurityConfig.java` |
| 8 | Ghi vào `../API.md` §4, §5 và bảng §3 ở file này | |

### Quy tắc bắt buộc

- **Không nhận `accountId` từ URL hay body.** Luôn `SecurityUtil.currentAccountId()`.
- **Truy cập tài nguyên theo `id` thì lọc kèm `accountId`** (như `findByIdAndAccountId`). Không phải của mình → `404`, không phải `403`.
- **Tiền dùng `BigDecimal`**, so sánh bằng `compareTo`, không dùng `equals` hay `double`.
- **Không trả entity ra ngoài** — luôn qua DTO response.

---

## 5. Lưu ý khi sửa code

| Chỗ | Vì sao |
|---|---|
| `AccountServiceImpl.deleteAccount` phải xoá `balance` **trước** `account` | `balance` đã nằm trong bộ nhớ Hibernate và trỏ tới `account` → nếu chỉ xoá `account`, Hibernate **lặng lẽ bỏ lệnh xoá**, API vẫn trả 204. Đã gặp khi test |
| `Balance.version` | Hai lệnh ghi số dư cùng lúc thì lệnh sau thất bại → `409 CONCURRENT_UPDATE`. Đã thử 10 lệnh nạp song song: 2 thành công, 8 bị chặn, số dư đúng |
| `schema-extra.sql` dùng `DROP CONSTRAINT IF EXISTS` + `ADD` | Spring tách script theo `;` nên không dùng được khối `DO $$`. Viết thế này để khởi động lại không lỗi |
| `data.sql` dùng `ON CONFLICT` / `NOT EXISTS` | Chạy mỗi lần khởi động — không được chèn trùng |
| Thẻ hợp lệ phải kiểm tra **mỗi lần dùng** | Hạn thẻ tự đổi theo ngày, không có lệnh ghi nào |

---

## 6. Chạy và thử

```bash
# Từ thư mục gốc — dựng lại riêng bank-service
docker compose up -d --build bank-service

# Xem log
docker logs -f bank-service

# Đăng nhập lấy token
curl -s localhost:8080/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"a@test.com","password":"Test@1234"}'

# Gọi API có token
curl -s localhost:8080/accounts/me -H "Authorization: Bearer <token>"
```

### Tài khoản mẫu

| Email | Số dư | Thẻ | Dùng để thử |
|---|---|---|---|
| `a@test.com` | 1.000.000 | `ACTIVE`, còn hạn | Case thành công |
| `b@test.com` | 0 | không có | Xoá tài khoản thành công |
| `c@test.com` | 500.000 | `INACTIVE` | Rút tiền bị chặn — thẻ tắt |
| `d@test.com` | 500.000 | `ACTIVE`, **hết hạn** 2020 | Rút tiền bị chặn — thẻ hết hạn |

Mật khẩu chung: `Test@1234`.

### Biến môi trường

| Biến | Mặc định | Trong Docker |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | `postgres-bank` / `5432` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `root` | như trên |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | `redis` / `6379` |
| `JWT_SECRET` | chuỗi mẫu trong `application.yml` | **đổi khi chạy thật** |
| `JWT_EXPIRATION_MS` | `3600000` (1 giờ) | |

---

## 7. Còn phải làm

| Việc | Backlog `ARCHITECTURE2.md` §10 |
|---|---|
| Redis cache cho API 3 và 9, cập nhật ngay khi nạp / rút | mục 16, 17 |
| API 12 thanh toán + `PaymentClient` gọi `payment-service` | mục 22 |
| Postman collection — mỗi API ≥ 1 case thành công + 1 case thất bại | mục 18 |
| Unit test JUnit5 + Mockito — 2 test cho mỗi API | mục 28 |
| CORS cho frontend | mục 26 |
