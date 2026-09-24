# Thiết kế Database

> Dùng cho `bank-service`. Hai service còn lại không có database.
> PostgreSQL 16. Chi tiết kiến trúc xem `ARCHITECTURE2.md`.

---

## 1. Thứ tự làm

| Bước | Việc |
|---|---|
| 1 | Chốt quy ước chung (§3) |
| 2 | Chốt cột từng bảng (§4) |
| 3 | Chốt ràng buộc + vòng đời trạng thái (§5, §6) |
| 4 | Viết 4 entity + 4 repository |
| 5 | Viết `schema-extra.sql` — index + CHECK |
| 6 | Seed dữ liệu mẫu, chạy thử |

Ba bước đầu là **chốt trên giấy**, chưa gõ code. Làm ngược thứ tự — gõ entity trước rồi mới nghĩ ràng buộc — thì mỗi lần đổi ý phải sửa entity, sửa `schema-extra.sql`, rồi xoá database làm lại.

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
| `expiry_date` | DATE | ✗ | Dùng để xét thẻ hợp lệ — xem dưới |
| `status` | VARCHAR(20) | ✗ | `ACTIVE` / `INACTIVE` |
| `created_at` | TIMESTAMP | ✗ | |

**`ON DELETE RESTRICT` chứ không phải CASCADE.** Đề bài yêu cầu chặn xoá tài khoản khi còn thẻ — để database chặn luôn là lớp bảo vệ thứ hai, phòng khi code sót.

**`expiry_date` không phải cột trang trí.** Đề bài nhắc **3 lần**: *"Chỉ thẻ hợp lệ mới đc phép hoạt động"*. Thẻ hợp lệ nghĩa là thoả **cả hai** điều kiện:

```
status = 'ACTIVE'   VÀ   expiry_date >= hôm nay
```

Chỉ kiểm tra `status` là thiếu — một thẻ `ACTIVE` nhưng hết hạn từ năm ngoái vẫn rút được tiền. Điều kiện này kiểm tra ở Service, áp dụng cho **rút tiền** và **thanh toán** (xem §5 mục 10).

Không đặt CHECK `expiry_date >= CURRENT_DATE` ở database, vì CHECK chỉ chạy lúc ghi: một thẻ hợp lệ hôm nay sẽ tự hết hạn ngày mai mà không có lệnh ghi nào — CHECK không bắt được, và nếu có thì lại chặn cả việc lưu thẻ cũ. Đây là luật đọc, không phải luật ghi.

### 4.4 `transaction`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `id` | BIGSERIAL | ✗ | PK |
| `account_id` | BIGINT | ✗ | FK → `account(id)` **ON DELETE CASCADE** |
| `card_id` | BIGINT | **✓** | FK → `card(id)` **ON DELETE CASCADE** |
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

#### Vì sao hai khoá ngoại này là CASCADE chứ không phải RESTRICT

`RESTRICT` chặn xoá khi còn **bất kỳ** dòng con nào. Dùng nó ở đây thì mâu thuẫn với chính đề bài:

> *"Xóa thẻ (chỉ xóa được nếu thẻ **không có giao dịch nào đang chờ xử lý**)"*

Đề bài chỉ chặn khi còn giao dịch `PENDING`. Nhưng `RESTRICT` chặn cả khi thẻ chỉ còn giao dịch `COMPLETED` đã xong từ lâu:

```
Thẻ có 5 giao dịch COMPLETED, 0 PENDING   → đề bài nói PHẢI xoá được
  Service kiểm tra: không có PENDING  ✓ cho qua
  Database:         RESTRICT          ✗ ném lỗi
  Người dùng nhận:  500 thay vì 204
```

Tương tự với `account_id`: `RESTRICT` khiến tài khoản nào từng nạp tiền một lần là **vĩnh viễn không xoá được**, dù đã xoá hết thẻ và rút số dư về 0 — trong khi đề bài chỉ đặt hai điều kiện *"không có thẻ liên kết và số dư bằng 0"*.

`CASCADE` cũng nhất quán với `balance`, vốn đã CASCADE từ đầu — cả hai đều là dữ liệu con của `account`.

**Không dùng `SET NULL`** cho `card_id`, dù cột này cho phép NULL. Một giao dịch `WITHDRAW` cũ sau khi thẻ bị xoá sẽ có `card_id = NULL`, vi phạm ngay CHECK ở §5 mục 6. Hai ràng buộc đó không thể cùng tồn tại.

**Đánh đổi:** xoá thẻ hoặc tài khoản thì mất lịch sử giao dịch liên quan. Ngân hàng thật không làm vậy (họ xoá mềm), nhưng đề bài có hẳn API `DELETE` với điều kiện rõ ràng, tức chấp nhận xoá thật. Luật nghiệp vụ vẫn được giữ đủ ở tầng Service (§5 mục 7, 8).

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
| 7 | Không xoá được tài khoản còn thẻ **hoặc số dư ≠ 0** | FK RESTRICT trên `card.account_id` + kiểm tra ở Service |
| 8 | Không xoá được thẻ còn giao dịch **`PENDING`** | Chỉ kiểm tra ở Service |
| 9 | Tổng tiền thật = `available` + `hold` | Logic ở Service |
| 10 | Chỉ thẻ **hợp lệ** mới rút tiền / thanh toán được | Chỉ kiểm tra ở Service |

**Ràng buộc 7** làm ở **cả hai nơi**: Service kiểm tra để trả lỗi 400 cho người dùng hiểu; `card.account_id` vẫn giữ `ON DELETE RESTRICT` để database chặn, phòng trường hợp code sót đường nào đó. "Số dư bằng 0" nghĩa là **cả `available_balance` và `hold_balance` đều bằng 0** — còn tiền đang bị giữ thì vẫn là còn tiền.

**Ràng buộc 8** chỉ làm được ở Service, không nhờ được database. Điều kiện của đề bài là *"không có giao dịch nào **đang chờ xử lý**"* — tức lọc theo `status = 'PENDING'`. Khoá ngoại không diễn đạt được điều kiện có lọc: `RESTRICT` chặn mọi giao dịch, kể cả `COMPLETED` (xem §4.4). Nên `transaction.card_id` để CASCADE, còn Service truy vấn `idx_txn_card_status` (§8) để kiểm tra.

**Ràng buộc 10** — "thẻ hợp lệ" = `status = 'ACTIVE'` **và** `expiry_date >= hôm nay` (§4.3). Đây là điều kiện đọc, thay đổi theo ngày mà không có lệnh ghi nào, nên không đặt được bằng CHECK.

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

#### `status` mới là một nửa điều kiện

`ACTIVE` chưa đủ để thẻ hoạt động được. Đề bài đòi **thẻ hợp lệ**, và thẻ hợp lệ cần cả hai:

| Điều kiện | Nguồn | Đổi khi nào |
|---|---|---|
| `status = 'ACTIVE'` | cột `status` | khi có lệnh ghi |
| `expiry_date >= hôm nay` | cột `expiry_date` | **tự đổi theo thời gian** |

Vế thứ hai là chỗ dễ quên nhất: không ai chạy lệnh nào cả, nhưng qua nửa đêm thì một thẻ đang hợp lệ thành hết hạn. Vì vậy phải kiểm tra **mỗi lần dùng thẻ**, không phải kiểm tra một lần rồi lưu kết quả.

Áp dụng ở: `POST /balance/withdraw` và `POST /payments`. Không áp dụng ở `POST /balance/deposit` — nạp tiền không cần thẻ (§4.4).

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

**Ai đổi trạng thái?** Cả ba lần chuyển đều do `bank-service` làm, trong cùng một Service:

| Chuyển | Xảy ra khi |
|---|---|
| → `PENDING` | nhận `POST /payments`, trước khi gọi `payment-service` |
| `PENDING` → `COMPLETED` | `payment-service` trả `202` |
| `PENDING` → `FAILED` | gọi `payment-service` lỗi hoặc timeout |

`notification-service` **không** đổi trạng thái — nó chỉ log ra console theo đúng đề bài. Lý do chọn cách này ở `ARCHITECTURE2.md` §7.2.

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

### Hai cái bẫy làm app không khởi động được

Cả hai đều làm app sập ngay lúc chạy, nên chốt trước cho đỡ mất công debug.

**Bẫy 1 — sai thứ tự.** Spring Boot mặc định chạy file SQL khởi tạo **trước** khi Hibernate tạo bảng. Để nguyên thì lần chạy đầu tiên sẽ `ALTER TABLE` một bảng chưa tồn tại → sập. Phải bật cờ hoãn trong `application.yml`:

```yaml
spring:
  jpa:
    defer-datasource-initialization: true   # chạy SQL SAU khi Hibernate tạo bảng
  sql:
    init:
      mode: always
```

**Bẫy 2 — chạy lần thứ hai thì sập.** PostgreSQL có `CREATE INDEX IF NOT EXISTS`, nhưng **không có** `ADD CONSTRAINT IF NOT EXISTS`. Khởi động lần 2 sẽ báo constraint đã tồn tại. Nên index viết thẳng, còn CHECK phải bọc điều kiện:

```sql
CREATE INDEX IF NOT EXISTS idx_card_account ON card(account_id);

DO $$ BEGIN
    ALTER TABLE balance ADD CONSTRAINT chk_available_non_negative
        CHECK (available_balance >= 0);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
```

Viết kiểu này thì chạy lại bao nhiêu lần cũng được — cần thiết vì app khởi động lại rất nhiều lần trong lúc phát triển.

Khi entity thay đổi nhiều, cách nhanh nhất là **xoá sạch database làm lại** — đang phát triển, không có dữ liệu thật để giữ.

---

## 10. Dữ liệu mẫu

Cần seed sẵn để Postman chạy được ngay và không phải đăng ký tay mỗi lần dựng lại DB.

| Tài khoản | Email | Số dư khả dụng | Thẻ |
|---|---|---|---|
| Nguyễn Văn A | `a@test.com` | 1.000.000 | 1 thẻ `ACTIVE`, hạn **2030-12-31** |
| Trần Thị B | `b@test.com` | 0 | không có thẻ |
| Lê Văn C | `c@test.com` | 500.000 | 1 thẻ `INACTIVE`, hạn 2030-12-31 |
| Phạm Thị D | `d@test.com` | 500.000 | 1 thẻ `ACTIVE`, hạn **2020-01-31** (đã hết hạn) |

Bốn tài khoản này phục vụ đúng các case cần test:

- **A** — case thành công: rút tiền được, thẻ `ACTIVE` và còn hạn
- **B** — xoá tài khoản thành công (không thẻ, số dư 0)
- **C** — case thất bại: rút tiền bị chặn vì thẻ `INACTIVE`
- **D** — case thất bại: rút tiền bị chặn vì thẻ **đã hết hạn**, dù `status` vẫn là `ACTIVE`

C và D tách riêng vì "thẻ hợp lệ" có hai vế (§4.3) và Postman cần một case thất bại cho **mỗi vế**. Chỉ có C thì vế hết hạn không ai kiểm tra, và đó đúng là vế dễ quên code nhất.

### Mật khẩu

Cả bốn tài khoản dùng chung mật khẩu gốc **`Test@1234`** — đủ mạnh để qua validate (§3 `API.md`), và chỉ cần nhớ một chuỗi khi chạy Postman.

Trong database phải lưu dạng **hash BCrypt**, không lưu thô. Sinh hash một lần rồi dán vào file seed, không hard-code mật khẩu thô ở bất cứ đâu trong source.

---

## 11. Checklist

- [ ] Không cột tiền nào dùng `FLOAT`/`DOUBLE`
- [ ] Java dùng `BigDecimal`, không dùng `double`
- [ ] Enum lưu chuỗi, không lưu số
- [ ] `balance.account_id` vừa PK vừa FK
- [ ] `balance` có cột `version`
- [ ] `card.account_id` dùng ON DELETE **RESTRICT**
- [ ] `transaction.account_id` và `transaction.card_id` dùng ON DELETE **CASCADE**
- [ ] `transaction.card_id` cho phép NULL
- [ ] Xoá thẻ chỉ chặn khi còn giao dịch **`PENDING`**, không chặn khi chỉ còn `COMPLETED`
- [ ] Rút tiền / thanh toán kiểm tra **cả `status = ACTIVE` lẫn `expiry_date >= hôm nay`**
- [ ] Xoá tài khoản kiểm tra `available_balance` **và** `hold_balance` đều bằng 0
- [ ] Có `schema-extra.sql` với 3 index và các CHECK
- [ ] `defer-datasource-initialization: true` đã bật, app chạy lại lần 2 không sập
- [ ] Mật khẩu lưu hash BCrypt
- [ ] Có dữ liệu mẫu **4** tài khoản, trong đó có 1 thẻ đã hết hạn
