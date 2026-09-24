# Đặc tả API

> `bank-service` — `http://localhost:8080`
> Tài liệu này để **backend và frontend làm song song**: frontend dựng màn hình bằng dữ liệu giả theo đúng hình dạng dưới đây, không cần chờ backend xong.

---

## 1. Quy ước chung

| Việc | Quy ước |
|---|---|
| Content-Type | `application/json` |
| Xác thực | Header `Authorization: Bearer <token>` |
| Tiền | Số, tối đa 2 chữ số thập phân. **Không** gửi dạng chuỗi |
| Thời gian | ISO-8601: `2026-09-24T10:30:00` |
| `accountId` | **Không bao giờ** nằm trong URL hay body — luôn lấy từ token |

### Mã HTTP dùng trong hệ thống

| Mã | Khi nào |
|---|---|
| `200` | GET / PUT thành công |
| `201` | POST tạo mới thành công |
| `202` | Đã nhận, đang xử lý bất đồng bộ — chỉ dùng cho `/payments` |
| `204` | DELETE thành công, không có nội dung trả về |
| `400` | Dữ liệu sai, hoặc vi phạm ràng buộc nghiệp vụ |
| `401` | Chưa đăng nhập, token sai hoặc hết hạn, sai mật khẩu |
| `404` | Không tìm thấy — **hoặc tài nguyên không thuộc về mình** |
| `409` | Xung đột — email đã tồn tại, hoặc hai giao dịch ghi cùng lúc |

> **Vì sao truy cập tài nguyên của người khác trả `404` chứ không phải `403`?**
> Trả `403` là vô tình xác nhận "thẻ id này có tồn tại, chỉ là không phải của anh". Kẻ xấu dò được id nào có thật. Trả `404` thì không lộ gì.

---

## 2. Định dạng lỗi — dùng chung cho mọi API

Mọi lỗi đều trả về đúng một hình dạng này. Frontend chỉ cần viết **một** component hiển thị lỗi.

```json
{
  "timestamp": "2026-09-24T10:30:00",
  "status": 400,
  "code": "INSUFFICIENT_BALANCE",
  "message": "Số dư khả dụng không đủ",
  "path": "/balance/withdraw"
}
```

Riêng lỗi validate có thêm `fieldErrors`:

```json
{
  "timestamp": "2026-09-24T10:30:00",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Dữ liệu không hợp lệ",
  "path": "/accounts",
  "fieldErrors": {
    "email": "Email không đúng định dạng",
    "phoneNumber": "Số điện thoại phải có 10 chữ số"
  }
}
```

### Bảng mã lỗi

| `code` | HTTP | Ý nghĩa |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Dữ liệu đầu vào sai định dạng |
| `INSUFFICIENT_BALANCE` | 400 | Số dư khả dụng không đủ |
| `CARD_NOT_ACTIVE` | 400 | Thẻ không ở trạng thái `ACTIVE` hoặc đã hết hạn |
| `CARD_HAS_PENDING_TRANSACTION` | 400 | Thẻ còn giao dịch đang chờ, không xoá được |
| `ACCOUNT_HAS_CARDS` | 400 | Tài khoản còn thẻ, không xoá được |
| `ACCOUNT_BALANCE_NOT_ZERO` | 400 | Số dư khác 0, không xoá được |
| `INVALID_CREDENTIALS` | 401 | Sai email hoặc mật khẩu |
| `UNAUTHORIZED` | 401 | Thiếu token, token sai hoặc hết hạn |
| `ACCOUNT_NOT_FOUND` | 404 | Không tìm thấy tài khoản |
| `CARD_NOT_FOUND` | 404 | Không tìm thấy thẻ, hoặc thẻ không thuộc về mình |
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã được đăng ký |
| `CONCURRENT_UPDATE` | 409 | Hai giao dịch ghi cùng lúc, thử lại |

---

## 3. Quy tắc validate

| Trường | Quy tắc |
|---|---|
| `customerName` | Bắt buộc, 2–100 ký tự |
| `email` | Bắt buộc, đúng định dạng email, tối đa 150 ký tự, không trùng |
| `phoneNumber` | Bắt buộc, 10 chữ số, bắt đầu bằng `0` |
| `password` | Bắt buộc, tối thiểu 8 ký tự |
| `amount` | Bắt buộc, **> 0**, tối đa 2 chữ số thập phân |
| `currency` | 3 ký tự viết hoa. Mặc định `VND` nếu không gửi |
| `cardType` | `DEBIT` hoặc `CREDIT` |
| `expiryDate` | Bắt buộc, phải là ngày **trong tương lai** |

---

## 4. Danh sách API

**12 API công khai** + 1 API nội bộ.

| # | Method | Path | Token? |
|---|---|---|---|
| 1 | POST | `/auth/login` | ❌ |
| 2 | POST | `/accounts` | ❌ |
| 3 | GET | `/accounts/me` | ✅ |
| 4 | PUT | `/accounts/me` | ✅ |
| 5 | DELETE | `/accounts/me` | ✅ |
| 6 | GET | `/accounts/me/cards` | ✅ |
| 7 | POST | `/accounts/me/cards` | ✅ |
| 8 | DELETE | `/cards/{id}` | ✅ |
| 9 | GET | `/accounts/me/balance` | ✅ |
| 10 | POST | `/balance/deposit` | ✅ |
| 11 | POST | `/balance/withdraw` | ✅ |
| 12 | POST | `/payments` | ✅ |
| — | POST | `payment-service:8081/payments` | ❌ nội bộ |

---

## 5. Chi tiết

### 1. `POST /auth/login` — Đăng nhập

Không cần token.

**Request**

```json
{ "email": "a@test.com", "password": "password123" }
```

**Response `200`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "expiresIn": 3600
}
```

| Lỗi | Mã |
|---|---|
| Sai email hoặc mật khẩu | `401 INVALID_CREDENTIALS` |

> Sai email và sai mật khẩu trả **cùng một** thông báo. Nếu tách riêng thì kẻ xấu dò được email nào có đăng ký.

---

### 2. `POST /accounts` — Đăng ký tài khoản

Không cần token. Tự tạo số dư = 0.

**Request**

```json
{
  "customerName": "Nguyễn Văn A",
  "email": "a@test.com",
  "phoneNumber": "0901234567",
  "password": "password123"
}
```

**Response `201`**

```json
{
  "accountId": 1,
  "customerName": "Nguyễn Văn A",
  "email": "a@test.com",
  "phoneNumber": "0901234567",
  "createdAt": "2026-09-24T10:30:00"
}
```

| Lỗi | Mã |
|---|---|
| Email đã tồn tại | `409 EMAIL_ALREADY_EXISTS` |
| Dữ liệu sai định dạng | `400 VALIDATION_FAILED` |

> Không trả về `password`, kể cả dạng hash.

---

### 3. `GET /accounts/me` — Xem thông tin tài khoản

Trả kèm số dư. Có cache Redis, TTL 10 phút.

**Response `200`**

```json
{
  "accountId": 1,
  "customerName": "Nguyễn Văn A",
  "email": "a@test.com",
  "phoneNumber": "0901234567",
  "createdAt": "2026-09-24T10:30:00",
  "balance": {
    "availableBalance": 200000.00,
    "holdBalance": 800000.00
  }
}
```

---

### 4. `PUT /accounts/me` — Cập nhật thông tin

Chỉ sửa được email và số điện thoại. Xoá cache sau khi sửa.

**Request** — gửi trường nào sửa trường đó

```json
{ "email": "new@test.com", "phoneNumber": "0909999999" }
```

**Response `200`** — giống API số 3.

| Lỗi | Mã |
|---|---|
| Email mới đã có người dùng | `409 EMAIL_ALREADY_EXISTS` |

> Không cho sửa `customerName` và `password` qua API này — đề bài chỉ yêu cầu email và số điện thoại.

---

### 5. `DELETE /accounts/me` — Xoá tài khoản

**Response `204`** — không có nội dung.

| Lỗi | Mã |
|---|---|
| Còn thẻ liên kết | `400 ACCOUNT_HAS_CARDS` |
| Số dư khác 0 | `400 ACCOUNT_BALANCE_NOT_ZERO` |

> Kiểm tra **cả `availableBalance` lẫn `holdBalance`** — tiền đang bị giữ cũng là tiền.

---

### 6. `GET /accounts/me/cards` — Danh sách thẻ

**Response `200`**

```json
[
  {
    "cardId": 10,
    "cardType": "DEBIT",
    "expiryDate": "2030-12-31",
    "status": "ACTIVE",
    "createdAt": "2026-09-24T10:30:00"
  }
]
```

Không có thẻ nào thì trả mảng rỗng `[]`, **không phải** `404`.

---

### 7. `POST /accounts/me/cards` — Tạo thẻ

**Request**

```json
{ "cardType": "DEBIT", "expiryDate": "2030-12-31" }
```

**Response `201`** — giống một phần tử ở API số 6.

| Lỗi | Mã |
|---|---|
| Tài khoản không tồn tại | `404 ACCOUNT_NOT_FOUND` |
| `expiryDate` trong quá khứ | `400 VALIDATION_FAILED` |

> Thẻ mới tạo mặc định `ACTIVE` — vì đề bài không có API kích hoạt thẻ.

---

### 8. `DELETE /cards/{id}` — Xoá thẻ

**Response `204`**

| Lỗi | Mã |
|---|---|
| Thẻ không tồn tại, **hoặc không phải thẻ của mình** | `404 CARD_NOT_FOUND` |
| Thẻ còn giao dịch `PENDING` | `400 CARD_HAS_PENDING_TRANSACTION` |

> Đây là API duy nhất còn `{id}` trên URL. Vẫn an toàn vì Service kiểm tra thẻ có thuộc về người đang đăng nhập không.

---

### 9. `GET /accounts/me/balance` — Xem số dư

**Response `200`**

```json
{
  "accountId": 1,
  "availableBalance": 200000.00,
  "holdBalance": 800000.00,
  "totalBalance": 1000000.00
}
```

`totalBalance` = `availableBalance` + `holdBalance`, tính ở Service, không lưu trong database.

---

### 10. `POST /balance/deposit` — Nạp tiền

**Không cần thẻ** — tiền đi vào tài khoản.

**Request**

```json
{ "amount": 1000000, "currency": "VND" }
```

**Response `200`** — giống API số 9, kèm `transactionId`.

```json
{
  "transactionId": 100,
  "accountId": 1,
  "availableBalance": 1200000.00,
  "holdBalance": 800000.00,
  "totalBalance": 2000000.00
}
```

| Lỗi | Mã |
|---|---|
| `amount` ≤ 0 | `400 VALIDATION_FAILED` |

Cập nhật lại cache Redis ngay, không chờ TTL.

---

### 11. `POST /balance/withdraw` — Rút tiền

**Bắt buộc có thẻ** và thẻ phải `ACTIVE`.

**Request**

```json
{ "amount": 500000, "currency": "VND", "cardId": 10 }
```

**Response `200`** — giống API số 10.

| Lỗi | Mã |
|---|---|
| Số dư khả dụng không đủ | `400 INSUFFICIENT_BALANCE` |
| Thẻ `INACTIVE` hoặc đã hết hạn | `400 CARD_NOT_ACTIVE` |
| Thẻ không tồn tại hoặc không phải của mình | `404 CARD_NOT_FOUND` |
| Hai lệnh rút cùng lúc | `409 CONCURRENT_UPDATE` |

> So sánh với **`availableBalance`**, không phải `totalBalance`. Tiền đang bị giữ không được tiêu.

---

### 12. `POST /payments` — Thanh toán

Bất đồng bộ. Trả về **`202`**, không phải `200` — vì lúc trả về thì thông báo chưa hề được gửi.

**Request**

```json
{ "amount": 800000, "currency": "VND", "cardId": 10 }
```

**Response `202`**

```json
{
  "transactionId": 101,
  "status": "PENDING",
  "amount": 800000.00,
  "currency": "VND",
  "message": "Yêu cầu thanh toán đã được tiếp nhận"
}
```

| Lỗi | Mã |
|---|---|
| Số dư khả dụng không đủ | `400 INSUFFICIENT_BALANCE` |
| Thẻ không `ACTIVE` | `400 CARD_NOT_ACTIVE` |
| Thẻ không phải của mình | `404 CARD_NOT_FOUND` |

**Việc xảy ra bên trong:** ghi `Transaction` = `PENDING` → chuyển `amount` từ `availableBalance` sang `holdBalance` → gọi HTTP sang `payment-service` → nhận `202` thì đánh `Transaction` = `COMPLETED` và trừ `holdBalance` → trả `202` cho client.

> `payment-service` chết hoặc timeout thì đánh giao dịch `FAILED` và hoàn tiền từ `holdBalance` về `availableBalance`.
>
> Không có bước nào khác chốt `COMPLETED` — `notification-service` chỉ log, không đụng vào DB (`ARCHITECTURE2.md` §7.2).

---

### API nội bộ — `POST payment-service:8081/payments`

`bank-service` gọi, không lộ ra ngoài, không cần token.

**Request**

```json
{ "paymentId": 101, "accountId": 1, "amount": 800000, "currency": "VND" }
```

**Response `202`** — rỗng.

`payment-service` **không kiểm tra gì cả**. Nó chỉ đóng gói 4 trường này thành message và thả vào queue `payment.queue`. Toàn bộ luật đã được kiểm tra ở `bank-service`.

---

## 6. Dành cho frontend

Frontend dựng 10 màn bằng dữ liệu giả theo đúng hình dạng trên, **không chờ backend**.

| Màn | API |
|---|---|
| Đăng nhập | 1 |
| Đăng ký | 2 |
| Dashboard | 3 |
| Sửa thông tin | 4 |
| Xoá tài khoản | 5 |
| Danh sách thẻ | 6 |
| Tạo thẻ | 7 |
| Xoá thẻ | 8 |
| Nạp tiền | 10 |
| Rút tiền | 11 |
| Thanh toán | 12 |

### Ba điều frontend phải xử lý

**Gặp `401` thì xoá token và về trang đăng nhập.** Xử lý một chỗ trong axios interceptor, không rải khắp nơi.

**Hiển thị lỗi theo `code`, không theo `message`.** `message` có thể đổi câu chữ bất cứ lúc nào; `code` thì cố định.

**`202` không phải là "đã xong".** Màn thanh toán phải hiện "đang xử lý", không hiện "thanh toán thành công".

---

## 7. Dữ liệu mẫu để test

Theo `DATABASE.md` §10. Mật khẩu chung: `password123`.

| Email | Số dư | Thẻ | Dùng để test |
|---|---|---|---|
| `a@test.com` | 1.000.000 | 1 thẻ `ACTIVE` (id 10) | Case thành công |
| `b@test.com` | 0 | không có | Xoá tài khoản thành công |
| `c@test.com` | 500.000 | 1 thẻ `INACTIVE` (id 12) | Rút tiền bị chặn |

### Case thất bại bắt buộc có trong Postman

Đề bài: **mỗi API ít nhất 1 case thành công + 1 case thất bại.** Bảng dưới phủ đủ 12/12 API.

| API | Case fail |
|---|---|
| 1 | Sai mật khẩu → 401 |
| 2 | Email trùng → 409 |
| 3 | Không gửi token → 401 |
| 4 | Email sai định dạng → 400 |
| 5 | Xoá tài khoản A (còn thẻ) → 400 |
| 6 | Token hết hạn → 401 |
| 7 | `expiryDate` quá khứ → 400 |
| 8 | Xoá thẻ của người khác → 404 |
| 9 | Token sai chữ ký → 401 |
| 10 | `amount` = 0 → 400 |
| 11 | Rút bằng thẻ `INACTIVE` → 400 |
| 11 | Rút quá số dư → 400 |
| 12 | Thanh toán quá số dư → 400 |
