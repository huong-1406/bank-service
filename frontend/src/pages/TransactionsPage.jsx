import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Tab, Tabs } from 'react-bootstrap'
import axiosClient, { getErrorMessage } from '../api/axiosClient'
import { formatDate, formatMoney } from '../utils/format'

// Một form dùng chung cho cả 3 tab: nạp tiền, rút tiền, thanh toán
function TransactionForm({ needCard, cards, submitLabel, onSubmit }) {
  const [amount, setAmount] = useState('')
  const [cardId, setCardId] = useState('')
  const [result, setResult] = useState(null)   // { type: 'success' | 'danger', text }
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setResult(null)
    setLoading(true)
    try {
      const body = needCard ? { amount: Number(amount), cardId: Number(cardId) } : { amount: Number(amount) }
      const text = await onSubmit(body)
      setResult({ type: 'success', text })
      setAmount('')
    } catch (err) {
      setResult({ type: 'danger', text: getErrorMessage(err) })
    } finally {
      setLoading(false)
    }
  }

  return (
    <Form onSubmit={handleSubmit} style={{ maxWidth: 420 }}>
      {result && <Alert variant={result.type}>{result.text}</Alert>}

      <Form.Group className="mb-3">
        <Form.Label>Số tiền (₫)</Form.Label>
        <Form.Control type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)}
                      placeholder="100000" required />
      </Form.Group>

      {needCard && (
        <Form.Group className="mb-3">
          <Form.Label>Chọn thẻ</Form.Label>
          <Form.Select value={cardId} onChange={(e) => setCardId(e.target.value)} required>
            <option value="">-- Chọn thẻ --</option>
            {cards.map((c) => (
              <option key={c.cardId} value={c.cardId}>
                Thẻ {c.cardId} - {c.cardType} - {c.status} - hết hạn {formatDate(c.expiryDate)}
              </option>
            ))}
          </Form.Select>
          {cards.length === 0 && <Form.Text className="text-danger">Bạn chưa có thẻ. Vào trang Thẻ để tạo.</Form.Text>}
        </Form.Group>
      )}

      <Button type="submit" disabled={loading}>{loading ? 'Đang xử lý...' : submitLabel}</Button>
    </Form>
  )
}

export default function TransactionsPage() {
  const [balance, setBalance] = useState(null)
  const [cards, setCards] = useState([])

  const loadBalance = () => axiosClient.get('/accounts/me/balance').then((res) => setBalance(res.data))

  useEffect(() => {
    loadBalance()
    axiosClient.get('/accounts/me/cards').then((res) => setCards(res.data))
  }, [])

  // POST /balance/deposit — không cần thẻ
  const deposit = async (body) => {
    await axiosClient.post('/balance/deposit', body)
    await loadBalance()
    return `Nạp ${formatMoney(body.amount)} thành công`
  }

  // POST /balance/withdraw — thẻ phải hợp lệ, số dư phải đủ (backend kiểm tra)
  const withdraw = async (body) => {
    await axiosClient.post('/balance/withdraw', body)
    await loadBalance()
    return `Rút ${formatMoney(body.amount)} thành công`
  }

  // POST /payments — bank-service gọi payment-service, notification-service log thông báo
  const pay = async (body) => {
    const res = await axiosClient.post('/payments', body)
    await loadBalance()
    return `Thanh toán thành công ${formatMoney(body.amount)} (mã giao dịch ${res.data.transactionId})`
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="mb-0">Giao dịch</h3>
        {balance && (
          <Card bg="primary" text="white" className="px-3 py-2">
            Số dư khả dụng: <strong>{formatMoney(balance.availableBalance)}</strong>
          </Card>
        )}
      </div>

      <Card className="shadow-sm">
        <Card.Body>
          <Tabs defaultActiveKey="deposit" className="mb-4" mountOnEnter>
            <Tab eventKey="deposit" title="Nạp tiền">
              <TransactionForm needCard={false} cards={cards} submitLabel="Nạp tiền" onSubmit={deposit} />
            </Tab>
            <Tab eventKey="withdraw" title="Rút tiền">
              <TransactionForm needCard cards={cards} submitLabel="Rút tiền" onSubmit={withdraw} />
            </Tab>
            <Tab eventKey="payment" title="Thanh toán">
              <TransactionForm needCard cards={cards} submitLabel="Thanh toán" onSubmit={pay} />
            </Tab>
          </Tabs>
        </Card.Body>
      </Card>
    </div>
  )
}
