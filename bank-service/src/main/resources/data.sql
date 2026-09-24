-- Dữ liệu mẫu cho Postman, xem DATABASE.md §10.
-- Mật khẩu gốc cả 4 tài khoản: Test@1234 (dưới đây là hash BCrypt).
-- Chạy lại được: ON CONFLICT / NOT EXISTS, không ghi id cứng.

-- ===== Tài khoản =====
INSERT INTO account (customer_name, email, phone_number, password, created_at) VALUES
    ('Nguyễn Văn A', 'a@test.com', '0900000001', '$2a$10$EqolojwVKHDG9d1ZmUjbOObFHlVQrRubXIKT97P.moQqKEYdBiwCy', NOW()),
    ('Trần Thị B',   'b@test.com', '0900000002', '$2a$10$EqolojwVKHDG9d1ZmUjbOObFHlVQrRubXIKT97P.moQqKEYdBiwCy', NOW()),
    ('Lê Văn C',     'c@test.com', '0900000003', '$2a$10$EqolojwVKHDG9d1ZmUjbOObFHlVQrRubXIKT97P.moQqKEYdBiwCy', NOW()),
    ('Phạm Thị D',   'd@test.com', '0900000004', '$2a$10$EqolojwVKHDG9d1ZmUjbOObFHlVQrRubXIKT97P.moQqKEYdBiwCy', NOW())
ON CONFLICT DO NOTHING;

-- ===== Số dư =====
INSERT INTO balance (account_id, available_balance, hold_balance, version)
SELECT a.id, v.available, 0, 0
FROM account a
JOIN (VALUES ('a@test.com', 1000000), ('b@test.com', 0), ('c@test.com', 500000), ('d@test.com', 500000))
    AS v (email, available) ON a.email = v.email
ON CONFLICT (account_id) DO NOTHING;

-- ===== Thẻ =====
-- A: hợp lệ | C: INACTIVE | D: ACTIVE nhưng đã hết hạn | B: không có thẻ
INSERT INTO card (account_id, card_type, expiry_date, status, created_at)
SELECT a.id, 'DEBIT', v.expiry::date, v.status, NOW()
FROM account a
JOIN (VALUES ('a@test.com', '2030-12-31', 'ACTIVE'),
             ('c@test.com', '2030-12-31', 'INACTIVE'),
             ('d@test.com', '2020-01-31', 'ACTIVE'))
    AS v (email, expiry, status) ON a.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM card c WHERE c.account_id = a.id);
