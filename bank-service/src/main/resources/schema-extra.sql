-- Chạy SAU khi Hibernate tạo bảng (defer-datasource-initialization: true).
-- Phần Hibernate không tự tạo được: index, CHECK, DEFAULT. Xem DATABASE.md §5, §8, §9.
-- Mọi lệnh đều chạy lại được nhiều lần mà không lỗi.

-- ===== Index =====
CREATE INDEX IF NOT EXISTS idx_card_account ON card (account_id);
CREATE INDEX IF NOT EXISTS idx_txn_account ON transaction (account_id);
CREATE INDEX IF NOT EXISTS idx_txn_card_status ON transaction (card_id, status);

-- ===== CHECK =====
-- Spring tách script theo dấu ';' nên không dùng được khối DO $$ ... $$.
-- Thay bằng DROP IF EXISTS rồi ADD lại: kết quả giống hệt, chạy lại bao nhiêu lần cũng được.
ALTER TABLE balance DROP CONSTRAINT IF EXISTS chk_balance_available_non_negative;
ALTER TABLE balance ADD CONSTRAINT chk_balance_available_non_negative CHECK (available_balance >= 0);

ALTER TABLE balance DROP CONSTRAINT IF EXISTS chk_balance_hold_non_negative;
ALTER TABLE balance ADD CONSTRAINT chk_balance_hold_non_negative CHECK (hold_balance >= 0);

ALTER TABLE transaction DROP CONSTRAINT IF EXISTS chk_txn_amount_positive;
ALTER TABLE transaction ADD CONSTRAINT chk_txn_amount_positive CHECK (amount > 0);

-- Chỉ DEPOSIT được phép không có thẻ; WITHDRAW và PAYMENT bắt buộc có thẻ
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS chk_txn_card_required;
ALTER TABLE transaction ADD CONSTRAINT chk_txn_card_required CHECK (type = 'DEPOSIT' OR card_id IS NOT NULL);

-- ===== DEFAULT =====
ALTER TABLE balance ALTER COLUMN available_balance SET DEFAULT 0;
ALTER TABLE balance ALTER COLUMN hold_balance SET DEFAULT 0;
ALTER TABLE balance ALTER COLUMN version SET DEFAULT 0;
