# Postman collection — bank-service

File `bank-service.postman_collection.json` chứa **38 request** cho **12 API**. Mỗi API có ít nhất 1 case thành công và 1 case thất bại, đúng yêu cầu đề bài.

## Chạy trong Postman
1. Chạy hệ thống: `docker compose up -d`
2. Postman → **Import** → chọn file `bank-service.postman_collection.json`
3. Chuột phải collection **bank-service** → **Run** → **Run bank-service**

Chạy theo thứ tự từ trên xuống. Token và `cardId` được tự lưu, không phải dán tay.

## Chạy bằng dòng lệnh (không cần cài Postman)
```bash
docker run --rm --network host -v "$PWD/postman:/etc/newman" postman/newman:alpine run bank-service.postman_collection.json
```

## Cách hoạt động
- Mỗi lần chạy **tự đăng ký một tài khoản mới** (email, SĐT khác nhau), cuối cùng tự xoá. Không đụng vào dữ liệu mẫu A, B, C, D.
- Case thẻ `INACTIVE` dùng tài khoản C, case thẻ hết hạn dùng tài khoản D.
- Đổi địa chỉ server: sửa biến `baseUrl` của collection (mặc định `http://localhost:8080`).

## 6 thư mục
| Thư mục | API |
|---|---|
| 1. Đăng ký và đăng nhập | 1, 2 |
| 2. Tài khoản | 3, 4 |
| 3. Thẻ | 6, 7 |
| 4. Số dư | 9, 10, 11 |
| 5. Thanh toán | 12 |
| 6. Xoá thẻ, xoá tài khoản | 5, 8 |

API 12 chưa có case "payment-service tắt → 503" trong collection, vì phải tắt container bằng tay: `docker stop payment-service`.
