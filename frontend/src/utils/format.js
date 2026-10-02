// 1000000 → "1.000.000 ₫"
export function formatMoney(value) {
  return Number(value ?? 0).toLocaleString('vi-VN') + ' ₫'
}

// "2031-12-31" → "31/12/2031"
export function formatDate(value) {
  return new Date(value).toLocaleDateString('vi-VN')
}
