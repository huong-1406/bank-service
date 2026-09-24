  néu gi# Bank Service — Kiến trúc v2 (bám đề bài)

> Tài liệu kiến trúc **duy nhất** của dự án, viết theo đề bài gốc.
> Hệ thống: **3 service Spring Boot** (`bank-service`, `payment-service`, `notification-service`) + **frontend React** riêng.
> Kèm theo: `DATABASE.md` (thiết kế CSDL) · `API.md` (đặc tả API) · `PLAN.md` (lịch làm việc).
> Cập nhật: 2026-09-24.

---

## 0. Giả định & phạm vi

### 0.1 Hai giả định đang chờ chốt

| # | Giả định đang dùng trong tài liệu này | Nếu đổi thì sao |
|---|---|---|
| **A1** | **3 service riêng** (`bank-service`, `payment-service`, `notification-service`), Maven multi-module | Nếu chọn monolith → xem §12 |
| **A2** | **Frontend = React (SPA riêng)**, project độc lập, deploy bằng nginx | Nếu chọn Thymeleaf nhúng trong `bank-service` → xem §12 |

### 0.2 Điều chưa kiểm chứng

Link Figma của đề bài: <https://www.figma.com/design/bTDzXJpTuMwuqv2S4vKPRy/Banking--Copy-?node-id=0-1&p=f>

**Tôi chưa mở link này.** Danh sách màn hình ở §8 là **suy ra từ tập API**, không phải đọc từ Figma. Phải đối chiếu lại với Figma thật trước khi bắt tay làm frontend — số màn và luồng có thể khác.

### 0.3 Ba ràng buộc nghiệp vụ (đề bài nhắc lại 3 lần)

1. **Chỉ thẻ hợp lệ mới được phép hoạt động** → card phải `ACTIVE` và chưa hết hạn.
2. **Không được tạo thẻ cho tài khoản không tồn tại.**
3. **Không được trừ tiền nếu số dư khả dụng không đủ.**

### 0.4 Ba quy tắc phân tầng (đề bài yêu cầu)

- Phần xử lý **in/out** → Controller layer
- Phần **logic** → Service layer
- Phần **tương tác DB** → Repository layer

---

## 1. Tổng quan hệ thống

```
                        ┌──────────────────────────┐
                        │   frontend (React SPA)    │
                        │   nginx            :3000  │
                        │   project độc lập, tách   │
                        │   hẳn khỏi bank-service   │
                        └────────────┬──────────────┘
                                     │ HTTP + JWT (cần CORS)
┌────────────────────────────────────▼─────────────────────────────────┐
│                      bank-service        :8080                        │
│   CRUD Account/Card/Balance · JWT · Redis cache · Postgres            │
│   POST /payments ──────────────┐                                      │
└────────────────────────────────┼──────────────────────────────────────┘
          │            │         │ HTTP (RestClient)
          │            │         ▼
     ┌────▼────┐  ┌────▼────┐  ┌──────────────────────────┐
     │Postgres │  │  Redis  │  │ payment-service   :8081   │
     │  :5432  │  │  :6379  │  │ nhận HTTP → đẩy message   │
     └─────────┘  └─────────┘  └────────────┬──────────────┘
                                             │ JMS send
                                  ┌──────────▼──────────┐
                                  │   ActiveMQ  :61616   │
                                  │  queue payment.queue │
                                  └──────────┬──────────┘
                                             │ JMS consume
                               ┌─────────────▼──────────────────┐
                               │ notification-service   :8082    │
                               │ log "Payment confirmed for      │
                               │      paymentId: 12345"          │
                               └─────────────────────────────────┘
```

| Thành phần | Port | Stack | Có DB? | Có Redis? | Có JWT? | Số file ước tính |
|---|---|---|---|---|---|---|
| `frontend` | 3000 | React + Vite + nginx | ❌ | ❌ | giữ token | ~25 |
| `bank-service` | 8080 | Spring Boot | ✅ Postgres | ✅ | ✅ | ~42 |
| `payment-service` | 8081 | Spring Boot | ❌ | ❌ | ❌ | ~6 |
| `notification-service` | 8082 | Spring Boot | ❌ | ❌ | ❌ | ~4 |

> 2 service phụ rất nhẹ — gần như chỉ có boilerplate. Toàn bộ khối lượng backend nằm ở `bank-service`.
> `frontend` là project **Node/React riêng**, không nằm trong Maven multi-module, build bằng `npm`.

---

## 2. Kiến trúc layer (MVC) của bank-service

Giữ nguyên từ v1 — đây là phần đề bài chấm kỹ nhất (§0.4).

```
┌───────────────────────────────────────────────────┐
│                    CONTROLLER                       │
│  AccountController · CardController                 │
│  BalanceController · AuthController                 │
│  PaymentController                                  │
│  - nhận HTTP request (DTO request)                  │
│  - validate input, gọi Service                      │
│  - map kết quả ra DTO response, trả HTTP response   │
│  - KHÔNG chứa business logic                        │
└───────────────────────┬───────────────────────────┘
                        │ gọi method
┌───────────────────────▼───────────────────────────┐
│                      SERVICE                        │
│  AccountService · CardService                       │
│  BalanceService · AuthService · PaymentService      │
│  - toàn bộ business rule / ràng buộc:               │
│    · card ACTIVE + chưa hết hạn mới được giao dịch  │
│    · không tạo card cho account không tồn tại       │
│    · withdraw chặn nếu availableBalance không đủ    │
│    · xóa account chỉ khi hết card & balance = 0     │
│    · xóa card chỉ khi không còn Transaction PENDING │
│  - sinh JWT, lấy accountId từ SecurityContext       │
└───────────────────────┬───────────────────────────┘
                        │ gọi method
┌───────────────────────▼───────────────────────────┐
│                     REPOSITORY                      │
│  AccountRepository · CardRepository                 │
│  BalanceRepository · TransactionRepository          │
│  - interface extends JpaRepository                  │
│  - chỉ thao tác DB, không chứa logic nghiệp vụ      │
└───────────────────────┬───────────────────────────┘
                        │ JPA / SQL
┌───────────────────────▼───────────────────────────┐
│                     PostgreSQL                      │
│      bảng: account · card · balance · transaction   │
└───────────────────────────────────────────────────┘
```

---

## 3. Cấu trúc thư mục (Maven multi-module)

```
bank-system/                          ← pom cha, packaging=pom
│
├── pom.xml                           ← <modules> gom 3 service
├── docker-compose.yml                ← 7 service: 3 hạ tầng + 3 backend + 1 frontend
├── ARCHITECTURE2.md
│
├── bank-service/                     ← :8080 — toàn bộ nghiệp vụ
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/bank/bankservice/
│       │   ├── BankServiceApplication.java
│       │   │
│       │   ├── entity/               Account · Balance · Card · Transaction
│       │   ├── repository/           4 interface JpaRepository
│       │   ├── service/              interface
│       │   │   └── impl/             business logic — nơi check ràng buộc
│       │   ├── controller/           Account · Card · Balance · Auth · Payment
│       │   ├── dto/
│       │   │   ├── request/
│       │   │   └── response/
│       │   ├── exception/            custom exception + GlobalExceptionHandler
│       │   ├── security/             JwtUtil · JwtAuthenticationFilter
│       │   │                          SecurityConfig · SecurityUtil
│       │   ├── config/               RedisConfig · RestClientConfig · CorsConfig
│       │   └── client/               PaymentClient (gọi HTTP sang payment-service)
│       └── resources/
│           └── application.yml       ← KHÔNG còn templates/ và static/
│
├── payment-service/                  ← :8081 — HTTP vào, JMS ra
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/bank/paymentservice/
│       ├── PaymentServiceApplication.java
│       ├── config/JmsConfig.java
│       ├── controller/PaymentController.java
│       ├── dto/PaymentRequest.java
│       ├── dto/PaymentMessage.java   {paymentId, accountId, amount, currency}
│       └── producer/PaymentProducer.java
│
├── notification-service/             ← :8082 — JMS vào, log ra
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/bank/notificationservice/
│       ├── NotificationServiceApplication.java
│       ├── config/JmsConfig.java
│       ├── dto/PaymentMessage.java
│       └── listener/NotificationListener.java
│
└── frontend/                         ← :3000 — React SPA, KHÔNG phải Maven module
    ├── Dockerfile                    build bằng node → serve bằng nginx
    ├── nginx.conf
    ├── package.json
    ├── vite.config.js
    ├── .env                          VITE_API_BASE_URL
    ├── index.html
    └── src/
        ├── main.jsx                  điểm khởi động
        ├── App.jsx                   khai báo router
        ├── api/
        │   ├── axiosClient.js        interceptor tự gắn token, tự bắt 401
        │   ├── authApi.js
        │   ├── accountApi.js
        │   ├── cardApi.js
        │   ├── balanceApi.js
        │   └── paymentApi.js
        ├── context/AuthContext.jsx   giữ token + thông tin đăng nhập
        ├── routes/PrivateRoute.jsx   chặn vào trang khi chưa login
        ├── pages/                    10 màn ở §8.1
        ├── components/               Layout · Header · Sidebar · Toast · Modal
        └── styles/
```

---

## 4. Sơ đồ thực thể (ERD)

```
                    ┌───────────────────────┐
                    │        Account         │
                    │ accountId (PK)         │
                    │ customerName           │
                    │ email        (unique)  │
                    │ phoneNumber            │
                    │ password     (BCrypt)  │
                    └───────────┬─────┬──────┘
                    1─1  │      │1─*  │1─*
              ┌──────────┘      │     └──────────┐
              ▼                 ▼                ▼
      ┌────────────────┐ ┌──────────────┐ ┌─────────────────────┐
      │    Balance     │ │     Card     │ │    Transaction      │
      │ accountId      │ │ cardId (PK)  │ │ transactionId (PK)  │
      │   (PK, FK)     │ │ accountId FK │ │ accountId (FK)      │
      │ availableBalance│ │ cardType     │ │ cardId (FK,nullable)│
      │ holdBalance    │ │  DEBIT/CREDIT│ │ amount              │
      └────────────────┘ │ expiryDate   │ │ currency            │
                         │ status       │◄┤ type                │
                         │ ACTIVE /     │ │ status              │
                         │ INACTIVE     │ │  PENDING/COMPLETED/ │
                         └──────────────┘ │  FAILED             │
                                   0..1─* │ createdAt           │
                                          └─────────────────────┘
```

### 4.1 Ghi chú về `Transaction`

Đề bài **không liệt kê** `Transaction` trong danh sách thực thể, nhưng lại yêu cầu:

> *"Xóa thẻ (chỉ xóa được nếu thẻ không có giao dịch nào đang chờ xử lý)"*

→ Không có bảng `transaction` với trạng thái `PENDING` thì **không thể làm được yêu cầu này**. Vì vậy `Transaction` là thực thể **bắt buộc phải suy ra**, dù đề bài không nói thẳng.

### 4.2 Ghi chú về `holdBalance`

Đề bài định nghĩa: *"Số dư đang bị giữ (ví dụ: chờ xử lý giao dịch)"* — tức chính là tiền của các `Transaction` đang `PENDING`.

**Quy ước dùng:**

| Thời điểm | `availableBalance` | `holdBalance` |
|---|---|---|
| Tạo payment (Transaction `PENDING`) | `− amount` | `+ amount` |
| Payment `COMPLETED` | không đổi | `− amount` (tiền ra hẳn) |
| Payment `FAILED` | `+ amount` (hoàn lại) | `− amount` |

Nhờ vậy `holdBalance` có ý nghĩa thật và chặn được double-spend.

### 4.3 Quy ước chung

| Việc | Quy ước |
|---|---|
| Tên bảng | `snake_case`, số ít: `account`, `card`, `balance`, `transaction` |
| Tên cột | `snake_case`: `available_balance`, `created_at` |
| Khóa chính | `BIGSERIAL` (số tự tăng) — đơn giản, đủ dùng cho bài này |
| Enum | Lưu **`VARCHAR`**, không lưu số |
| Tiền | **`DECIMAL(19,2)`** — tuyệt đối không `FLOAT`/`DOUBLE` |
| Thời gian | `TIMESTAMP` |

**Hai quy ước trên là lỗi kinh điển nếu làm sai:**

**Tiền phải là `DECIMAL`.** `FLOAT`/`DOUBLE` lưu số thập phân **không chính xác** — `0.1 + 0.2` ra `0.30000000000000004`. Cộng dồn vài nghìn giao dịch là số dư lệch. Ngân hàng không chấp nhận điều đó. Trong Java tương ứng là `BigDecimal`, không phải `double`.

**Enum phải lưu chuỗi.** Nếu lưu số theo thứ tự khai báo, sau này chỉ cần chèn thêm một giá trị vào giữa danh sách là **toàn bộ dữ liệu cũ hiểu sai** — thẻ `ACTIVE` bỗng thành `INACTIVE`. Lưu chuỗi thì đọc DB cũng hiểu ngay.

### 4.4 Bảng `account`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `customer_name` | VARCHAR(100) | ✗ | |
| `email` | VARCHAR(150) | ✗ | **UNIQUE** — dùng để đăng nhập |
| `phone_number` | VARCHAR(20) | ✗ | |
| `password` | VARCHAR(255) | ✗ | Lưu **hash BCrypt**, không lưu thô. 255 ký tự vì hash dài |
| `created_at` | TIMESTAMP | ✗ | |

### 4.5 Bảng `balance`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `account_id` | BIGINT | ✗ | **PK và FK cùng lúc** → ép quan hệ 1–1 |
| `available_balance` | DECIMAL(19,2) | ✗ | mặc định 0, **CHECK ≥ 0** |
| `hold_balance` | DECIMAL(19,2) | ✗ | mặc định 0, **CHECK ≥ 0** |
| `version` | BIGINT | ✗ | chống tranh chấp — xem §4.8 |

`account_id` vừa là khóa chính vừa là khóa ngoại. Cách này đảm bảo **một tài khoản chỉ có đúng một dòng số dư** ở mức database, không cần code kiểm tra.

FK `ON DELETE CASCADE` — xoá tài khoản thì dòng số dư đi theo.

### 4.6 Bảng `card`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `account_id` | BIGINT | ✗ | FK → `account(id)`, **ON DELETE RESTRICT** |
| `card_type` | VARCHAR(20) | ✗ | `DEBIT` / `CREDIT` |
| `expiry_date` | DATE | ✗ | |
| `status` | VARCHAR(20) | ✗ | `ACTIVE` / `INACTIVE` |
| `created_at` | TIMESTAMP | ✗ | |

`ON DELETE RESTRICT` chứ không phải CASCADE — vì đề bài yêu cầu **chặn xoá tài khoản khi còn thẻ**. Để database chặn luôn là lớp bảo vệ thứ hai, phòng khi code sót.

### 4.7 Bảng `transaction`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `account_id` | BIGINT | ✗ | FK → `account(id)` |
| `card_id` | BIGINT | **✓** | FK → `card(id)`, **ON DELETE RESTRICT** |
| `amount` | DECIMAL(19,2) | ✗ | **CHECK > 0** |
| `currency` | VARCHAR(3) | ✗ | `VND`, `USD`… |
| `type` | VARCHAR(20) | ✗ | `DEPOSIT` / `WITHDRAW` / `PAYMENT` |
| `status` | VARCHAR(20) | ✗ | `PENDING` / `COMPLETED` / `FAILED` |
| `created_at` | TIMESTAMP | ✗ | |
| `updated_at` | TIMESTAMP | ✓ | |

`card_id` **được phép NULL** — nạp tiền thì không liên quan tới thẻ nào.

### 4.8 Index cần tạo

`ddl-auto: update` tự tạo bảng nhưng **không tự tạo index** cho các truy vấn của mình. Ba index sau phải thêm tay:

| Index | Trên cột | Phục vụ |
|---|---|---|
| `idx_card_account` | `card(account_id)` | Liệt kê thẻ của tài khoản |
| `idx_txn_account` | `transaction(account_id)` | Truy vấn giao dịch theo tài khoản |
| `idx_txn_card_status` | `transaction(card_id, status)` | **Quan trọng nhất** — kiểm tra thẻ còn giao dịch `PENDING` trước khi xoá |

`idx_txn_card_status` là index ghép 2 cột. Không có nó thì mỗi lần xoá thẻ phải quét toàn bộ bảng giao dịch.

> **Không cần index cho `email`** — PostgreSQL tự tạo index khi khai `UNIQUE`. Thêm nữa là thừa.

Chi tiết đầy đủ xem `DATABASE.md`.

### 4.9 Chống tranh chấp khi trừ tiền

Tình huống: hai yêu cầu rút tiền vào **cùng lúc**. Cả hai cùng đọc số dư 1 triệu, cả hai đều thấy đủ, cả hai đều cho qua. Kết quả rút được 2 triệu từ tài khoản 1 triệu.

Đây không phải chuyện lý thuyết — nó xảy ra thật khi người dùng bấm nút hai lần.

**Cách xử lý đơn giản nhất: cột `version` trong bảng `balance`.** Mỗi lần ghi, database kiểm tra `version` có còn như lúc đọc không. Nếu người khác đã sửa trước thì lệnh ghi thất bại, ứng dụng báo lỗi cho người dùng thử lại.

Thêm một cột và một annotation, gần như không tốn thời gian. Nhưng nếu bỏ qua thì đây là lỗ hổng nghiêm trọng nhất của cả hệ thống.

### 4.10 Về `ddl-auto: update`

`application.yml` đang để `update` — Hibernate tự sinh bảng từ entity. Với 1 tuần thì dùng được, nhưng cần biết giới hạn:

| `ddl-auto: update` làm được | Không làm được |
|---|---|
| Tạo bảng, cột, khóa ngoại | Tạo index tự định nghĩa (§4.8) |
| Thêm cột mới | Tạo `CHECK` constraint (§4.5, §4.7) |
| | Xoá cột đã bỏ khỏi entity |

Nên viết một file `schema-extra.sql` chứa phần index và CHECK, chạy bổ sung sau khi Hibernate tạo xong bảng.

Khi entity thay đổi nhiều, cách nhanh nhất là **xoá sạch database làm lại** — đang giai đoạn phát triển, không có dữ liệu thật để giữ.

---

## 5. Luồng JWT (Tuần 1+2, ngày 4-5)

Yêu cầu đề bài: *"Thay vì phải gửi thông tin account_id lên qua body hoặc param, hãy lấy nó từ JWT"*.

```
Client              AuthController          JwtFilter            Controller
  │  POST /auth/login {email,password}          │                    │
  │─────────────────────►│                      │                    │
  │       BCrypt.matches + JwtUtil.generate(accountId)                │
  │◄─────────────────────│ 200 OK { token }     │                    │
  │                                                                   │
  ══════════════════ mọi request kế tiếp ═══════════════════════════
  │  Header: Authorization: Bearer <token>      │                    │
  │─────────────────────────────────────────────►│                    │
  │            (parse & validate, extract accountId)                  │
  │                                              │ SecurityContext    │
  │                                              │  .accountId = ...  │
  │                                              │────────────────────►│
  │◄─────────────────────────────────────────────────────────────────│
  │       response — accountId đọc từ token, KHÔNG từ body/param     │
```

### 5.1 Đổi URL sau khi tích hợp JWT

| Trước (ngày 1-3) | Sau (ngày 4-5) |
|---|---|
| `GET /accounts/{id}` | `GET /accounts/me` |
| `PUT /accounts/{id}` | `PUT /accounts/me` |
| `DELETE /accounts/{id}` | `DELETE /accounts/me` |
| `GET /accounts/{id}/cards` | `GET /accounts/me/cards` |
| `POST /accounts/{id}/cards` | `POST /accounts/me/cards` |
| `GET /accounts/{id}/balance` | `GET /accounts/me/balance` |

> ⚠️ Bước này sửa lại **toàn bộ endpoint đã viết ở ngày 1-3**. Commit riêng, và viết Postman collection **sau** bước này chứ đừng viết trước.

### 5.2 Endpoint không cần token

`POST /auth/login` và `POST /accounts` (đăng ký) phải `permitAll`, nếu không sẽ không ai tạo được tài khoản đầu tiên.

---

## 6. Luồng thanh toán qua ActiveMQ (Tuần 3, ngày 2-3)

Theo đúng mô tả đề bài: `bank-service` gọi HTTP sang `payment-service`, service này đẩy message vào queue, `notification-service` consume và log.

```
Client      bank-service        payment-service      ActiveMQ      notification-service
  │ POST /payments   │                 │                 │                  │
  │─────────────────►│                 │                 │                  │
  │                  │ (1) INSERT Transaction PENDING    │                  │
  │                  │ (2) available −= amount           │                  │
  │                  │     hold      += amount           │                  │
  │                  │ (3) HTTP POST /payments           │                  │
  │                  │────────────────►│                 │                  │
  │                  │                 │ (4) send msg    │                  │
  │                  │                 │────────────────►│                  │
  │                  │◄────────────────│ 202 Accepted    │                  │
  │◄─────────────────│ 202 Accepted    │                 │                  │
  │                                                      │                  │
        ⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯ độ trễ bất đồng bộ ⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯⋯
                                                         │ (5) consume      │
                                                         │─────────────────►│
                                                         │  log.info("Payment
                                                         │  confirmed for
                                                         │  paymentId: 12345")
```

**Message payload** (4 field, đúng đề bài):

```json
{ "paymentId": "...", "accountId": "...", "amount": 100000, "currency": "VND" }
```

> `currency` là trường dễ bị bỏ sót — đề bài có, nhưng không nằm trong danh sách thực thể.

### 6.1 Ba điểm dễ sai

- **Commit DB trước khi gọi HTTP.** Nếu gọi `payment-service` trước khi commit `Transaction`, consumer có thể xử lý xong trước khi row tồn tại.
- **Listener phải idempotent.** ActiveMQ có thể redeliver message; không check thì log/trừ tiền 2 lần.
- **`bank-service` phải chịu được `payment-service` chết.** Gọi HTTP có timeout, lỗi thì đánh `Transaction` = `FAILED` và hoàn `holdBalance`.

### 6.2 Mở rộng ngoài đề bài (tùy chọn)

Đề bài chỉ yêu cầu `notification-service` **log ra console**, không yêu cầu chốt lại `Transaction`. Nếu muốn trạng thái `COMPLETED` có ý nghĩa thật:

> `notification-service` bắn message sang queue thứ hai `payment.completed.queue` → `bank-service` consume → `UPDATE Transaction status=COMPLETED` + `holdBalance −= amount`.

Làm vậy giữ được nguyên tắc **mỗi service chỉ đụng DB của mình**, không share database. Nhưng đây là phần cộng thêm — làm sau khi luồng chính đã chạy.

---

## 7. Cache-aside Redis (Tuần 3, ngày 1)

Chỉ áp dụng trong `bank-service`.

```
ĐỌC: GET /accounts/me                   GHI: deposit / withdraw
        │                                        │
        ▼                                        ▼
  Có trong Redis? ──yes──► Trả về từ Redis   Ghi DB (update balance)
        │no                       │                │
        ▼                         │                ▼
  Query PostgreSQL                │          Cập nhật Redis ngay
        │                         │           (KHÔNG chờ TTL)
        ▼                         │                │
  Set Redis (TTL 10 phút)         │                │
        │                         │                │
        └───────────►Response◄────┘                └─►Response
```

| Đối tượng cache | Annotation | Evict khi nào |
|---|---|---|
| Chi tiết account | `@Cacheable("account")` | `@CacheEvict` khi update (ngày 1-2) / delete account |
| Số dư | `@Cacheable("balance")` | `@CachePut` ngay sau deposit/withdraw |

TTL: **10 phút** (`spring.cache.redis.time-to-live: 600000`).

> ⚠️ Quên `@CacheEvict` ở bước update account là lỗi hay gặp nhất — GET sẽ trả dữ liệu cũ cho tới khi hết TTL.

---

## 8. Frontend (song song, 2 tuần)

> **Giả định A2: React (SPA riêng)** — project Node độc lập ở thư mục `frontend/`, build bằng Vite, chạy sau nginx ở cổng 3000. Không nằm trong Maven multi-module.

### 8.0 Stack đề xuất

| Việc | Thư viện | Vì sao |
|---|---|---|
| Build tool | **Vite** | Nhanh, cấu hình gần như bằng 0 |
| Điều hướng | **React Router** | 10 màn ở §8.1 cần route riêng |
| Gọi API | **Axios** | Có interceptor — gắn token và bắt lỗi 401 ở **một chỗ duy nhất** |
| Trạng thái đăng nhập | **Context API** | Chỉ cần giữ token + user, chưa cần Redux |
| Giao diện | **MUI** hoặc **Tailwind** | Chọn theo Figma; MUI nhanh hơn nếu Figma theo Material |
| Form | **React Hook Form** | Validate phía client trước khi gọi API |

> Chưa cần Redux / React Query. Bài này chỉ có 10 màn và 1 luồng đăng nhập — thêm vào là thừa.

### 8.1 Màn hình — SUY RA TỪ API, chưa đối chiếu Figma

⚠️ Danh sách dưới đây được suy ra từ tập API của đề bài. **Chưa mở link Figma** nên phải rà lại trước khi làm.

| # | Màn hình | API dùng |
|---|---|---|
| 1 | Đăng nhập | `POST /auth/login` |
| 2 | Đăng ký tài khoản | `POST /accounts` |
| 3 | Dashboard — thông tin tài khoản + số dư | `GET /accounts/me`, `GET /accounts/me/balance` |
| 4 | Sửa thông tin (email / SĐT) | `PUT /accounts/me` |
| 5 | Danh sách thẻ | `GET /accounts/me/cards` |
| 6 | Tạo thẻ mới | `POST /accounts/me/cards` |
| 7 | Xoá thẻ (có xác nhận) | `DELETE /cards/{id}` |
| 8 | Nạp tiền | `POST /balance/deposit` |
| 9 | Rút tiền | `POST /balance/withdraw` |
| 10 | Thanh toán | `POST /payments` |

### 8.2 Bốn vướng mắc kỹ thuật của SPA

#### (1) CORS — chắc chắn sẽ gặp

React chạy ở `localhost:3000`, API ở `localhost:8080`. Trình duyệt coi đây là **hai origin khác nhau** và sẽ chặn mọi request nếu backend không cho phép.

- Phải khai `CorsConfig` trong `bank-service`, cho phép origin của frontend, cho phép header `Authorization`.
- Cấu hình CORS phải nằm **trong `SecurityConfig`** (`http.cors(...)`), không chỉ khai `@CrossOrigin` lẻ tẻ — nếu không, Spring Security sẽ chặn trước khi tới controller.
- Trình duyệt gửi **preflight `OPTIONS`** trước request thật. Phải cho `OPTIONS` đi qua mà không cần token, nếu không mọi thứ hỏng hết.

> Triệu chứng điển hình: Postman gọi ngon lành nhưng trình duyệt báo lỗi CORS. Gặp lỗi này thì lỗi nằm ở backend, không phải React.

#### (2) Lưu token ở đâu

| Cách | Ưu | Nhược |
|---|---|---|
| `localStorage` | Đơn giản, sống qua reload trang | Script độc hại đọc được (XSS) |
| `sessionStorage` | Mất khi đóng tab | Vẫn dính XSS |
| HttpOnly cookie | JS không đọc được, an toàn hơn | Phức tạp hơn, dính thêm vấn đề CSRF + CORS credentials |

**Đề xuất cho bài này: `localStorage`** — đây là bài tập, đơn giản là ưu tiên. Nhưng **ghi rõ trong báo cáo** rằng sản phẩm thật nên dùng HttpOnly cookie. Người chấm biết anh hiểu đánh đổi sẽ được điểm cao hơn là làm đúng mà không giải thích.

#### (3) Gắn token vào mọi request

Đừng viết tay `headers: { Authorization: ... }` ở từng lời gọi API — sẽ quên chỗ này chỗ kia.

Dùng **axios interceptor** đặt ở `api/axiosClient.js`, làm 2 việc:
- **Request interceptor**: tự đọc token từ `localStorage` và gắn vào header cho mọi request.
- **Response interceptor**: gặp `401` thì xoá token và đẩy về trang login. Xử lý một chỗ, toàn bộ app hưởng.

#### (4) Chặn truy cập trang khi chưa đăng nhập

`PrivateRoute` bọc quanh các route cần token. Chưa đăng nhập thì chuyển hướng về `/login`.

Lưu ý: đây chỉ là **chặn ở giao diện cho đẹp**, không phải bảo mật. Bảo mật thật nằm ở backend — dù có vào được trang, không có token thì API vẫn trả 401.

### 8.3 Lộ trình frontend (chạy song song 2 tuần)

| Giai đoạn | Việc |
|---|---|
| Tuần 1 đầu | Mở Figma, chốt lại bảng §8.1. `npm create vite`, cài router + axios + thư viện UI |
| Tuần 1 | Layout chung (Header/Sidebar), `AuthContext`, `axiosClient`, `PrivateRoute`, màn login + đăng ký |
| Tuần 1 cuối | Dashboard + màn tài khoản — **dùng dữ liệu giả**, backend chưa xong vẫn làm được |
| Tuần 2 đầu | Màn thẻ + màn số dư. Backend ngày 1-3 xong thì bắt đầu nối API thật |
| Tuần 2 giữa | Màn thanh toán. Hiển thị lỗi trả về từ `GlobalExceptionHandler` |
| Tuần 2 cuối | Viết `Dockerfile` + `nginx.conf`, rà lại toàn bộ 10 màn |

> Frontend **không bị chặn** bởi backend: dựng UI với dữ liệu giả trước, nối API sau.

### 8.4 Hai chỗ frontend phải khớp với backend

**Đợi mục 14 rồi hãy nối API.** Mục 14 (§9) đổi hết URL từ `/accounts/{id}` sang `/accounts/me`. Nối API trước bước đó thì phải sửa lại toàn bộ `api/*.js`.

**Thống nhất định dạng lỗi.** `GlobalExceptionHandler` phải trả body lỗi cố định một kiểu, để React chỉ cần một component `Toast` đọc đúng một trường là hiện được mọi lỗi — thay vì mỗi API một kiểu.

### 8.5 nginx và biến môi trường

React build ra **file tĩnh**, không có server Node lúc chạy — nginx chỉ việc serve thư mục `dist/`.

Hai chỗ hay sập:

- **Refresh trang bị 404.** SPA chỉ có một `index.html`; vào thẳng `/cards` rồi F5 thì nginx đi tìm file `/cards` không có. Phải khai `try_files $uri /index.html` trong `nginx.conf`.
- **URL API bị nướng cứng vào bundle.** Vite thay `VITE_API_BASE_URL` **lúc build**, không phải lúc chạy. Nên khi build image Docker phải truyền biến này vào **build arg**, không phải `environment` trong compose.

---

## 9. Backlog theo đề bài

### Tuần 1+2 · Ngày 1-2 — Khởi động + Quản lý tài khoản

| # | Việc | Ràng buộc |
|---|---|---|
| 1 | Khởi tạo project, 4 entity + 4 repository | |
| 2 | `POST /accounts` — tạo tài khoản (tên, email, SĐT) | Tự tạo `Balance` = 0 cùng transaction |
| 3 | `PUT /accounts/{id}` — cập nhật | **Chỉ** email hoặc SĐT |
| 4 | `GET /accounts/{id}` — chi tiết | Trả kèm balance |
| 5 | `DELETE /accounts/{id}` | **Chỉ xoá nếu không có thẻ liên kết VÀ số dư = 0** |

### Tuần 1+2 · Ngày 2-3 — Quản lý thẻ

| # | Việc | Ràng buộc |
|---|---|---|
| 6 | `GET /accounts/{id}/cards` — liệt kê thẻ theo accountId | |
| 7 | `POST /accounts/{id}/cards` — tạo thẻ | **Chỉ tạo được nếu tài khoản đã tồn tại** |
| 8 | `DELETE /cards/{id}` — xoá thẻ | **Chỉ xoá nếu thẻ không có giao dịch đang chờ xử lý** |

### Tuần 1+2 · Ngày 3 — Quản lý số dư

| # | Việc | Ràng buộc |
|---|---|---|
| 9 | `GET /accounts/{id}/balance` | |
| 10 | `POST /balance/deposit` — thêm tiền | amount > 0 |
| 11 | `POST /balance/withdraw` — trừ tiền | **Kiểm tra số dư khả dụng** + thẻ phải hợp lệ |
| 12 | `GlobalExceptionHandler` | 404 / 400, body lỗi thống nhất |

### Tuần 1+2 · Ngày 4-5 — Spring Security + JWT

| # | Việc |
|---|---|
| 13 | `POST /auth/login`, `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig` |
| 14 | **Refactor toàn bộ mục 2-11**: bỏ `accountId` khỏi body/param, lấy từ JWT (§5.1) |

### Chuẩn bị — Cài Redis, ActiveMQ, Docker

| # | Việc |
|---|---|
| 15 | `docker compose up -d` cho postgres + redis + activemq, kiểm tra kết nối được |

### Tuần 3 · Ngày 1 — Spring Cache + Redis

| # | Việc |
|---|---|
| 16 | Cache chi tiết account (§7) |
| 17 | Cache số dư, cập nhật ngay khi deposit/withdraw, TTL 10 phút |
| 18 | **Postman collection** — mỗi API ≥1 case thành công + ≥1 case thất bại |

### Tuần 3 · Ngày 2-3 — ActiveMQ

| # | Việc |
|---|---|
| 19 | Tách Maven multi-module, dựng `payment-service` + `notification-service` |
| 20 | `payment-service`: nhận HTTP → đẩy message `{paymentId, accountId, amount, currency}` vào queue |
| 21 | `notification-service`: consume → log `"Payment confirmed for paymentId: 12345"` |
| 22 | `bank-service`: `POST /payments` → tạo Transaction PENDING + hold tiền → gọi HTTP sang payment-service |

### Tuần 3 · Ngày 4 — Docker

| # | Việc |
|---|---|
| 23 | Viết 3 `Dockerfile` backend (multi-stage: maven build → JRE run) |
| 24 | Viết `Dockerfile` frontend (node build → nginx serve) + `nginx.conf` có `try_files` |
| 25 | Thêm cả 4 app vào `docker-compose.yml`, `depends_on` theo healthcheck |
| 26 | Đổi `VITE_API_BASE_URL` và origin CORS sang tên container thay vì `localhost` |
| 27 | `docker compose up --build` chạy trọn bộ, chạy lại Postman + bấm thử UI phải pass |

### Tuần 3 · Ngày 5 — Unit test

| # | Việc |
|---|---|
| 28 | JUnit5 + Mockito, **2 unit test cho 1 API** (1 pass + 1 fail) |
| 29 | `./mvnw clean verify` xanh toàn bộ 3 module |

---

## 10. Lộ trình

| Tuần | Ngày | Backend | Frontend (song song) |
|---|---|---|---|
| 1+2 | 1-2 | Khởi động + Quản lý tài khoản | Đọc Figma, dựng Vite, layout chung, login/đăng ký |
| 1+2 | 2-3 | Quản lý thẻ | Dashboard + màn tài khoản (dữ liệu giả) |
| 1+2 | 3 | Quản lý số dư | Màn thẻ (dữ liệu giả) |
| 1+2 | 4-5 | Spring Security + JWT | Màn số dư. **Backend chốt xong `/accounts/me` mới nối API thật** |
| — | — | Cài Redis / ActiveMQ / Docker | Màn thanh toán, hiển thị lỗi, Dockerfile + nginx |
| 3 | 1 | Spring Cache + Redis + Postman | |
| 3 | 2-3 | ActiveMQ — 2 service riêng | |
| 3 | 4 | Docker — 4 image (3 backend + 1 frontend) | |
| 3 | 5 | Unit test | |

---

## 11. Quy ước Git (đề bài yêu cầu)

> *"Sử dụng GitLab, đẩy lên git theo từng chức năng, commit theo chuẩn, rõ ràng"*

- Repo trên **GitLab**, `git init` + push **ngay ngày đầu**, trước khi viết dòng code nào.
- **Mỗi mục backlog ở §9 = 1 commit.** Không gom nhiều chức năng vào một commit.
- Conventional Commits:

```
feat(account): them API tao tai khoan moi
feat(card):    chan tao the khi account khong ton tai
fix(balance):  kiem tra so du kha dung truoc khi tru tien
refactor(security): lay accountId tu JWT thay vi param
test(account): them unit test cho createAccount
chore(docker): them Dockerfile cho bank-service
```

---

## 12. Nếu đổi giả định

### 12.1 Nếu chọn monolith thay vì 3 service (đổi A1)

| Mục | Thay đổi |
|---|---|
| §1, §3 | Bỏ multi-module. `payment` và `notification` thành 2 package trong `bank-service`: `messaging/payment/PaymentProducer`, `messaging/notification/NotificationListener` |
| §6 | Bỏ bước gọi HTTP (3). Service gọi thẳng `PaymentProducer.send()` |
| §9 mục 19, 20, 22 | Gộp lại, giảm ~0.5 ngày |
| §9 mục 23, 24 | Chỉ 1 Dockerfile thay vì 3 |
| **Rủi ro** | Lệch câu *"tạo thêm 2 service riêng biệt"* của đề bài |

### 12.2 Nếu quay lại Thymeleaf thay vì React (đổi A2)

| Mục | Thay đổi |
|---|---|
| §1, §3 | Bỏ hẳn thư mục `frontend/`. Thêm lại `resources/templates/` + `resources/static/` + `ViewController` vào `bank-service` |
| §8.0 | Bỏ toàn bộ stack Node (Vite, React Router, Axios, Context) |
| §8.2 (1) | **Bỏ được CORS** — cùng origin, không còn vấn đề preflight |
| §8.2 (2)(3) | Token chuyển sang **HttpOnly cookie**; `JwtAuthenticationFilter` phải đọc được cả header lẫn cookie |
| §8.5 | Bỏ nginx và build arg — Thymeleaf nằm chung 1 container với backend |
| §9 mục 24, 26 | Bỏ, còn 3 Dockerfile thay vì 4 |
| **Đánh đổi** | Ít hạ tầng hơn, không CORS, chỉ 1 stack. Nhưng khó bám Figma sát, và trải nghiệm kém hơn SPA |

---

## 13. Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Mất source (đã xảy ra 1 lần) | Cao | Push GitLab **ngày đầu tiên**, commit sau mỗi mục §9 |
| Mục 14 (refactor JWT) làm vỡ 11 endpoint đã viết | Cao | Commit riêng; viết Postman **sau** mục 14 |
| Frontend chờ backend → dồn việc tuần 2 | Cao | Dựng UI bằng dữ liệu giả trước, nối API sau |
| Danh sách màn ở §8.1 lệch Figma | Trung bình | Mở Figma đối chiếu **ngay ngày 1**, đừng để tới tuần 2 |
| **CORS chặn frontend** — Postman chạy được, trình duyệt thì không | **Cao** | Cấu hình CORS trong `SecurityConfig`, cho `OPTIONS` qua không cần token (§8.2) |
| Nối API trước mục 14 → phải sửa lại toàn bộ `api/*.js` | Cao | Dùng dữ liệu giả cho tới khi backend chốt xong URL `/accounts/me` (§8.4) |
| F5 trang con bị 404 sau khi lên nginx | Trung bình | `try_files $uri /index.html` trong `nginx.conf` (§8.5) |
| `VITE_API_BASE_URL` sai vì truyền lúc chạy thay vì lúc build | Trung bình | Truyền qua **build arg** của Docker, không phải `environment` (§8.5) |
| `notification-service` xử lý message 2 lần | Trung bình | Listener idempotent (§6.1) |
| `payment-service` chết → tiền kẹt ở `holdBalance` | Trung bình | Timeout + đánh `FAILED` + hoàn tiền (§6.1) |
| Cấu hình `localhost` không chạy được trong Docker | Trung bình | Dùng biến env cho host ngay từ đầu, đừng để tới ngày 4 tuần 3 |
| `ddl-auto: update` không xoá cột cũ | Thấp | Drop DB làm lại khi entity đổi nhiều, vì đang giai đoạn dev |

---

## 14. Các bước bắt đầu dự án (Ngày 0)

> Mục tiêu ngày 0: **không viết một dòng nghiệp vụ nào**, chỉ dựng khung sao cho `mvn compile` xanh, 4 app khởi động được, hạ tầng chạy, và code đã nằm trên GitLab.
> Xong hết mục này mới bắt đầu backlog mục 1 ở §9.

### 14.1 Kiểm tra máy

| Cần có | Kiểm tra bằng | Yêu cầu |
|---|---|---|
| JDK | `java -version` | **21** |
| Maven | `./mvnw -version` | đã có wrapper, không cần cài |
| Docker | `docker --version` | có Compose v2 |
| Node | `node -v` | **20+** |
| Git | `git --version` | bất kỳ |

Thiếu Node thì cài trước, vì bước 14.8 cần.

### 14.2 Dựng lại cây thư mục thành multi-module

Hiện tại thư mục đang là **1 module đơn**. Cần chuyển thành cấu trúc §3:

1. Tạo 3 thư mục con: `bank-service/`, `payment-service/`, `notification-service/`, và `frontend/`.
2. Chuyển `src/` hiện tại vào trong `bank-service/`.
3. `pom.xml` ở gốc đổi thành **pom cha**: `<packaging>pom</packaging>`, khai `<modules>` gồm 3 service backend. Bỏ hết `<dependencies>` ở đây, chỉ giữ `<parent>` Spring Boot và `<properties>`.
4. Mỗi service con có `pom.xml` riêng, `<parent>` trỏ về pom gốc.

> `frontend/` **không** khai trong `<modules>` — nó là project Node, Maven không quản lý.

Giữ nguyên tên thư mục gốc cũng được, không bắt buộc đổi thành `bank-system`.

### 14.3 Git + GitLab — làm ngay, đừng để sau

Đề bài yêu cầu GitLab. Và dự án này **đã mất source một lần rồi**.

1. `git init` tại thư mục gốc.
2. Bổ sung `.gitignore` — file hiện tại mới chỉ có phần Java, cần thêm: `node_modules/`, `dist/`, `.env.local`, `frontend/.vite/`.
3. Tạo repo rỗng trên GitLab, `git remote add origin ...`.
4. Commit đầu tiên và push **ngay bây giờ**, kể cả khi mới chỉ có pom rỗng.

### 14.4 Khởi động hạ tầng

```
docker compose up -d postgres redis activemq
```

Kiểm tra từng cái:

| Dịch vụ | Cách kiểm tra | Dấu hiệu đúng |
|---|---|---|
| Postgres | `docker exec -it bank-service-postgres pg_isready` | `accepting connections` |
| Redis | `docker exec -it bank-service-redis redis-cli ping` | `PONG` |
| ActiveMQ | mở `http://localhost:8161` | vào được, login `admin/admin` |

> Giao diện ActiveMQ ở 8161 sẽ dùng nhiều ở tuần 3 để nhìn message vào/ra queue (§6).

### 14.5 Chia dependency cho từng module

Đây là chỗ hay làm ẩu — nhét hết mọi thứ vào mọi module.

| Module | Cần |
|---|---|
| **pom cha** | Chỉ `<parent>` Spring Boot + `<properties>` + `<modules>`. Không dependency |
| **bank-service** | web · data-jpa · postgresql · validation · lombok · security · jjwt (3 gói) · data-redis · cache · test · security-test |
| **payment-service** | web · **activemq** · lombok · test |
| **notification-service** | web · **activemq** · lombok · test |

Hai điểm đáng chú ý:

- **`bank-service` KHÔNG cần `activemq`.** Nó nói chuyện với `payment-service` bằng HTTP, không đụng queue. Chỉ thêm activemq vào đây nếu sau này làm phần mở rộng §6.2 (queue phản hồi).
- **`notification-service` vẫn nên có `web`** dù không phục vụ HTTP — để có endpoint health cho Docker healthcheck ở tuần 3.

### 14.6 Sửa `application.yml` theo biến môi trường

File hiện tại đang nướng cứng `jdbc:postgresql://localhost:5432/...`. Chạy trong Docker sẽ sai host.

Đổi ngay bây giờ, đừng để tới ngày 4 tuần 3:

- `localhost` trong URL datasource → `${DB_HOST:localhost}`
- Redis host đã dùng `${REDIS_HOST:localhost}` rồi, giữ nguyên
- ActiveMQ broker url đã dùng biến rồi, giữ nguyên

Rồi tạo thêm `application.yml` cho 2 service mới: `payment-service` port **8081**, `notification-service` port **8082**, cả hai trỏ tới cùng broker.

### 14.7 Chạy thử khung rỗng

Mỗi service chỉ cần đúng 1 class `*Application.java` là chạy được.

```
./mvnw clean compile          # phải xanh cả 3 module
./mvnw -pl bank-service spring-boot:run
```

Mốc đạt: cả 3 app khởi động không lỗi, chiếm đúng 3 port 8080/8081/8082. Chưa có API nào cũng không sao.

### 14.8 Khởi tạo frontend React

```
cd frontend
npm create vite@latest . -- --template react
npm install
npm install react-router-dom axios
npm install @mui/material @emotion/react @emotion/styled   # hoặc tailwind, tùy Figma
npm run dev
```

Mốc đạt: mở `http://localhost:3000` thấy trang mặc định của Vite.

> Vite mặc định chạy port 5173 — sửa thành 3000 trong `vite.config.js` cho khớp tài liệu, hoặc sửa tài liệu cho khớp Vite. Miễn là **thống nhất một chỗ**, vì con số này còn dùng lại ở cấu hình CORS và Docker.

### 14.9 Đối chiếu Figma — làm trong ngày 0, không để muộn

Mở link Figma ở §0.2, rà lại bảng 10 màn ở §8.1:

- Figma có màn nào mà bảng thiếu không?
- Bảng có màn nào Figma không có không?
- Có màn nào cần API mà đề bài **không hề yêu cầu** không? (ví dụ lịch sử giao dịch)

Câu hỏi thứ ba quan trọng nhất. Nếu Figma có màn cần API ngoài phạm vi đề bài, phải quyết định ngay: làm thêm API đó, hay bỏ màn đó. Phát hiện ở tuần 2 thì trở tay không kịp.

Cập nhật lại §8.1 sau khi rà xong.

### 14.10 Checklist hoàn thành ngày 0

- [ ] `java -version` ra 21, `node -v` ra 20+
- [ ] Cây thư mục đúng §3, có 4 thư mục con
- [ ] `./mvnw clean compile` xanh cả 3 module
- [ ] 3 app backend khởi động được, đúng port 8080/8081/8082
- [ ] `npm run dev` lên được frontend
- [ ] 3 container hạ tầng healthy, vào được ActiveMQ console
- [ ] `.gitignore` đã có `node_modules/` và `dist/`
- [ ] Đã push lên GitLab
- [ ] Đã đối chiếu Figma, §8.1 được cập nhật

### 14.11 Sau ngày 0 thì làm gì

Bắt đầu **backlog mục 1** ở §9 — 4 entity + 4 repository. Từ đây trở đi mỗi mục backlog là 1 commit, theo quy ước ở §11.

Frontend chạy song song theo lộ trình §8.3, dùng dữ liệu giả, **chưa nối API thật** cho tới khi backend xong mục 14 (§8.4).

> Thư mục backup `/home/toan/bank-service-source-backup-20260924/` giữ lại cho tới khi đã push GitLab thành công, sau đó xoá được.
