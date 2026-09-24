# Thiết kế Database

> Dùng cho `bank-service`. Hai service còn lại không có database.
> PostgreSQL 16. Chi tiết kiến trúc xem `ARCHITECTURE2.md`.

---

## 1. Kế hoạch làm — sáng ngày 1

| Bước | Việc | Thời gian |
|---|---|---|
| 1 | Chốt quy ước chung (§3) | 20 phút |
| 2 | Chốt cột từng bảng (§4) | 30 phút |
| 3 | Chốt ràng buộc + vòng đời trạng thái (§5, §6) | 30 phút |
| 4 | Viết 4 entity + 4 repository | 1h30 |
| 5 | Viết `schema-extra.sql` — index + CHECK | 30 phút |
| 6 | Seed dữ liệu mẫu, chạy thử | 30 phút |
| | **Tổng** | **~3h30** |

Cộng với dựng multi-module + Git + Docker (~2h) thì phần này **tràn sang đầu giờ chiều ngày 1**, không gọn trong buổi sáng. Cần biết trước để không hoảng.

---

## 2. Sơ đồ quan hệ

```
                    ┌─────────────────┐
                    │     account     │
                    │  id (PK)        │
                    └────┬───┬───┬────┘
              1─1        │   │   │        1─*
        ┌───────────────┘   │   └───────────────┐
        │              1─*  │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌──────────────┐   ┌─────────────────┐
│    balance    │   │     card     │   │   transaction   │
│ account_id    │   │ id (PK)      │   │ id (PK)         │
│  (PK + FK)    │   │ account_id FK│◄──┤ card_id FK NULL │
└───────────────┘   └──────────────┘   │ account_id FK   │
                              0..1─*    └─────────────────┘
```

| Quan hệ | Kiểu | Ghi chú |
|---|---|---|
| account → balance | 1–1 | Ép bằng cách cho `account_id` vừa là PK vừa là FK |
| account → card | 1–* | Một tài khoản nhiều thẻ |
| account → transaction | 1–* | |
| card → transaction | 0..1–* | Giao dịch có thể **không** gắn thẻ (nạp tiền) |

---

## 3. Quy ước chung

| Việc | Quy ước |
|---|---|
| Tên bảng | `snake_case`, số ít |
| Tên cột | `snake_case` |
| Khóa chính | `BIGSERIAL` — số tự tăng |
| Tiền | `DECIMAL(19,2)` ↔ `BigDecimal` trong Java |
| Enum | Lưu `VARCHAR` |
| Thời gian | `TIMESTAMP` |

### Vì sao tiền phải là `DECIMAL`

`FLOAT` và `DOUBLE` lưu số thập phân **không chính xác**. Trong hầu hết ngôn ngữ, `0.1 + 0.2` cho ra `0.30000000000000004`. Sai số rất nhỏ, nhưng cộng dồn qua hàng nghìn giao dịch thì số dư lệch thật.

`DECIMAL` lưu chính xác từng chữ số. Bên Java dùng `BigDecimal`, **không dùng `double`**.

`(19,2)` nghĩa là tối đa 19 chữ số, trong đó 2 chữ số sau dấu phẩy.

### Vì sao enum phải lưu chuỗi

Nếu lưu theo thứ tự khai báo — `ACTIVE` = 0, `INACTIVE` = 1 — thì sau này chỉ cần chèn thêm một giá trị vào giữa danh sách là toàn bộ dữ liệu cũ **hiểu sai hết**. Thẻ đang `ACTIVE` bỗng thành thứ khác.

Lưu chuỗi thì nhìn thẳng vào database cũng đọc hiểu, và thêm giá trị mới không ảnh hưởng gì.

---

## 4. Chi tiết các bảng

### 4.1 `account`

| Cột | Kiểu | Null | Ràng buộc | Ghi chú |
|---|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK | |
| `customer_name` | VARCHAR(100) | ✗ | | |
| `email` | VARCHAR(150) | ✗ | **UNIQUE** | Dùng để đăng nhập |
| `phone_number` | VARCHAR(20) | ✗ | | Đủ chỗ cho mã quốc gia |
| `password` | VARCHAR(255) | ✗ | | **Hash BCrypt**, không lưu thô |
| `created_at` | TIMESTAMP | ✗ | | |

`password` để 255 ký tự vì chuỗi hash BCrypt dài 60 ký tự, chừa dư phòng khi đổi thuật toán.

### 4.2 `balance`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `account_id` | BIGINT | ✗ | **PK + FK** → `account(id)` ON DELETE CASCADE |
| `available_balance` | DECIMAL(19,2) | ✗ | mặc định 0, CHECK ≥ 0 |
| `hold_balance` | DECIMAL(19,2) | ✗ | mặc định 0, CHECK ≥ 0 |
| `version` | BIGINT | ✗ | mặc định 0 — chống tranh chấp, xem §7 |

**`account_id` vừa là khóa chính vừa là khóa ngoại.** Đây là cách ép quan hệ 1–1 ở mức database: khóa chính không cho trùng, nên một tài khoản không thể có hai dòng số dư. Không cần code kiểm tra.

`ON DELETE CASCADE` — xoá tài khoản thì dòng số dư đi theo.

### 4.3 `card`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `account_id` | BIGINT | ✗ | FK → `account(id)` **ON DELETE RESTRICT** |
| `card_type` | VARCHAR(20) | ✗ | `DEBIT` / `CREDIT` |
| `expiry_date` | DATE | ✗ | |
| `status` | VARCHAR(20) | ✗ | `ACTIVE` / `INACTIVE` |
| `created_at` | TIMESTAMP | ✗ | |

**`ON DELETE RESTRICT` chứ không phải CASCADE.** Đề bài yêu cầu chặn xoá tài khoản khi còn thẻ — để database chặn luôn là lớp bảo vệ thứ hai, phòng khi code sót.

### 4.4 `transaction`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `account_id` | BIGINT | ✗ | FK → `account(id)` ON DELETE RESTRICT |
| `card_id` | BIGINT | **✓** | FK → `card(id)` **ON DELETE RESTRICT** |
| `amount` | DECIMAL(19,2) | ✗ | CHECK > 0 |
| `currency` | VARCHAR(3) | ✗ | `VND`, `USD`… |
| `type` | VARCHAR(20) | ✗ | `DEPOSIT` / `WITHDRAW` / `PAYMENT` |
| `status` | VARCHAR(20) | ✗ | `PENDING` / `COMPLETED` / `FAILED` |
| `created_at` | TIMESTAMP | ✗ | |
| `updated_at` | TIMESTAMP | ✓ | |

`card_id` **cho phép NULL**, nhưng chỉ đúng với một loại giao dịch:

| `type` | `card_id` | Vì sao |
|---|---|---|
| `DEPOSIT` | **NULL** | Tiền đi **vào** tài khoản — nhận lương, người khác chuyển khoản, nộp tiền mặt tại quầy. Không cần thẻ, thậm chí chưa có thẻ vẫn nhận được tiền |
| `WITHDRAW` | bắt buộc | Tiền đi **ra** — phải có công cụ để rút |
| `PAYMENT` | bắt buộc | Tiền đi **ra** — phải quẹt thẻ |

Nói ngắn gọn: **thẻ là chìa khóa để lấy tiền ra, không phải phễu để đổ tiền vào.**

Ràng buộc này được đảm bảo bằng CHECK ở §5 mục 6, không chỉ dựa vào code.

`amount` luôn là số dương. Tiền vào hay ra thì nhìn vào cột `type`, không dùng số âm — như vậy dễ kiểm tra và không sợ nhầm dấu.

> Bảng này **đề bài không liệt kê**, nhưng bắt buộc phải có: không có nó thì không thể làm yêu cầu *"xoá thẻ chỉ khi thẻ không có giao dịch đang chờ xử lý"*.

---

## 5. Ràng buộc toàn vẹn

Những điều **luôn đúng** trong mọi thời điểm:

| # | Ràng buộc | Đảm bảo bằng |
|---|---|---|
| 1 | `available_balance` ≥ 0 | CHECK |
| 2 | `hold_balance` ≥ 0 | CHECK |
| 3 | `amount` > 0 | CHECK |
| 4 | Mỗi tài khoản có **đúng một** dòng số dư | PK trùng FK |
| 5 | Email không trùng | UNIQUE |
| 6 | Giao dịch **rút tiền và thanh toán** phải có thẻ | CHECK: `type = 'DEPOSIT' OR card_id IS NOT NULL` |
| 7 | Không xoá được tài khoản còn thẻ | FK RESTRICT + kiểm tra ở Service |
| 8 | Không xoá được thẻ còn giao dịch | FK RESTRICT + kiểm tra ở Service |
| 9 | Tổng tiền thật = `available` + `hold` | Logic ở Service |

Ràng buộc 7 và 8 làm ở **cả hai nơi**: Service kiểm tra để trả lỗi 400 cho người dùng hiểu; database chặn để phòng trường hợp code sót đường nào đó.

---

## 6. Vòng đời trạng thái

### `card.status`

```
   tạo thẻ
      │
      ▼
   ACTIVE ◄──────► INACTIVE
```

**Quyết định cần chốt:** thẻ mới tạo mặc định là gì?

Đề bài không nói, và cũng **không có API kích hoạt thẻ**. Nếu mặc định `INACTIVE` thì thẻ vừa tạo xong không rút tiền được mà cũng không có cách nào bật lên.

→ **Chọn mặc định `ACTIVE`.**

### `transaction.status`

```
   tạo giao dịch
         │
         ▼
      PENDING
       ╱     ╲
      ▼       ▼
 COMPLETED  FAILED     ← hai trạng thái kết thúc, không đổi nữa
```

Ảnh hưởng tới số dư:

| Chuyển trạng thái | `available_balance` | `hold_balance` |
|---|---|---|
| → `PENDING` | − amount | + amount |
| `PENDING` → `COMPLETED` | không đổi | − amount |
| `PENDING` → `FAILED` | + amount | − amount |

Tiền chỉ **thật sự rời khỏi tài khoản** khi sang `COMPLETED`.

---

## 7. Đồng thời — chống trừ tiền hai lần

### Vấn đề

Hai yêu cầu rút 800k vào **cùng một lúc**, tài khoản có 1 triệu:

```
Yêu cầu A            Yêu cầu B
   │                     │
   ├─ đọc số dư: 1tr     │
   │                     ├─ đọc số dư: 1tr
   ├─ đủ tiền ✓          │
   │                     ├─ đủ tiền ✓
   ├─ ghi 200k           │
                         ├─ ghi 200k     ← ghi đè!
```

Kết quả: rút được 1,6 triệu từ tài khoản 1 triệu, mà số dư chỉ trừ 800k.

Chuyện này xảy ra thật khi người dùng bấm nút hai lần liên tiếp.

### Cách xử lý

Thêm cột **`version`** vào bảng `balance`. Mỗi lần ghi, database kiểm tra `version` có còn giống lúc đọc không:

- Còn giống → ghi thành công, `version` tăng lên 1
- Đã bị người khác sửa → **ghi thất bại**, ứng dụng báo người dùng thử lại

Bên JPA chỉ cần đánh dấu cột này là cột phiên bản, Hibernate tự lo phần còn lại.

**Chi phí: một cột và một annotation.** Bỏ qua thì đây là lỗ hổng nghiêm trọng nhất của hệ thống.

---

## 8. Truy vấn chính và index

| # | Truy vấn | Dùng ở | Index |
|---|---|---|---|
| 1 | Tìm tài khoản theo email | Đăng nhập | Tự có từ UNIQUE |
| 2 | Liệt kê thẻ của tài khoản | `GET /accounts/me/cards` | `idx_card_account` |
| 3 | Tài khoản còn thẻ không? | Xoá tài khoản | `idx_card_account` |
| 4 | Thẻ còn giao dịch `PENDING` không? | Xoá thẻ | `idx_txn_card_status` |
| 5 | Giao dịch của tài khoản | Thanh toán | `idx_txn_account` |
| 6 | Lấy số dư | Mọi nơi | Tự có từ PK |

### Chỉ cần thêm tay 3 index

| Index | Trên cột |
|---|---|
| `idx_card_account` | `card(account_id)` |
| `idx_txn_card_status` | `transaction(card_id, status)` |
| `idx_txn_account` | `transaction(account_id)` |

> **Không cần index cho `email`.** PostgreSQL tự tạo index khi khai `UNIQUE` — thêm nữa là thừa, chỉ tốn chỗ và làm chậm ghi.

`idx_txn_card_status` là index ghép 2 cột và quan trọng nhất. Không có nó thì mỗi lần xoá thẻ phải quét toàn bộ bảng giao dịch.

---

## 9. Giới hạn của `ddl-auto: update`

`application.yml` đang để `update` — Hibernate tự sinh bảng từ entity.

| Làm được | Không làm được |
|---|---|
| Tạo bảng, cột, khóa chính, khóa ngoại | Tạo index tự định nghĩa (§8) |
| Thêm cột mới | Tạo `CHECK` constraint (§5) |
| Tạo UNIQUE | Xoá cột đã bỏ khỏi entity |

→ Phải viết thêm file **`schema-extra.sql`** chứa 3 index và các CHECK, chạy sau khi Hibernate tạo bảng xong.

Khi entity thay đổi nhiều, cách nhanh nhất là **xoá sạch database làm lại** — đang phát triển, không có dữ liệu thật để giữ.

---

## 10. Dữ liệu mẫu

Cần seed sẵn để Postman chạy được ngay và không phải đăng ký tay mỗi lần dựng lại DB.

| Tài khoản | Email | Số dư khả dụng | Thẻ |
|---|---|---|---|
| Nguyễn Văn A | `a@test.com` | 1.000.000 | 1 thẻ `ACTIVE` |
| Trần Thị B | `b@test.com` | 0 | không có thẻ |
| Lê Văn C | `c@test.com` | 500.000 | 1 thẻ `INACTIVE` |

Ba tài khoản này phục vụ đúng các case cần test:

- **A** — case thành công: rút tiền được, có thẻ hợp lệ
- **B** — xoá tài khoản thành công (không thẻ, số dư 0)
- **C** — case thất bại: rút tiền bị chặn vì thẻ `INACTIVE`

Mật khẩu để giống nhau cho dễ nhớ, nhưng phải lưu dạng **hash BCrypt**, không lưu thô.

---

## 11. Checklist

- [ ] Không cột tiền nào dùng `FLOAT`/`DOUBLE`
- [ ] Java dùng `BigDecimal`, không dùng `double`
- [ ] Enum lưu chuỗi, không lưu số
- [ ] `balance.account_id` vừa PK vừa FK
- [ ] `balance` có cột `version`
- [ ] `card.account_id` dùng ON DELETE RESTRICT
- [ ] `transaction.card_id` cho phép NULL
- [ ] Có `schema-extra.sql` với 3 index và các CHECK
- [ ] Mật khẩu lưu hash BCrypt
- [ ] Có dữ liệu mẫu 3 tài khoản
