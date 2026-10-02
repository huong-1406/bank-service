import { useEffect, useState } from 'react'
import { Alert, Card, Col, Row, Spinner } from 'react-bootstrap'
import axiosClient, { getErrorMessage } from '../api/axiosClient'
import { formatMoney } from '../utils/format'

export default function DashboardPage() {
  const [account, setAccount] = useState(null)
  const [balance, setBalance] = useState(null)
  const [error, setError] = useState('')

  // Mở trang → gọi 2 API: thông tin tài khoản và số dư (cả hai có cache Redis ở backend)
  useEffect(() => {
    Promise.all([axiosClient.get('/accounts/me'), axiosClient.get('/accounts/me/balance')])
      .then(([accountRes, balanceRes]) => {
        setAccount(accountRes.data)
        setBalance(balanceRes.data)
      })
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  if (error) return <Alert variant="danger">{error}</Alert>
  if (!account || !balance) return <Spinner animation="border" />

  // 3 ô màu theo Figma
  const cards = [
    { title: 'Số dư khả dụng', value: balance.availableBalance, bg: 'primary' },
    { title: 'Số dư đang giữ', value: balance.holdBalance, bg: 'warning', text: 'dark' },
    { title: 'Tổng số dư', value: balance.totalBalance, bg: 'success' },
  ]

  return (
    <div>
      <h3 className="mb-4">Xin chào, {account.customerName}</h3>

      <Row className="g-3 mb-4">
        {cards.map((c) => (
          <Col md={4} key={c.title}>
            <Card bg={c.bg} text={c.text || 'white'} className="shadow-sm">
              <Card.Body>
                <Card.Subtitle className="mb-2">{c.title}</Card.Subtitle>
                <Card.Title as="h3">{formatMoney(c.value)}</Card.Title>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>

      <Card className="shadow-sm">
        <Card.Header>Thông tin tài khoản</Card.Header>
        <Card.Body>
          <p className="mb-1"><strong>Mã tài khoản:</strong> {account.accountId}</p>
          <p className="mb-1"><strong>Email:</strong> {account.email}</p>
          <p className="mb-1"><strong>Số điện thoại:</strong> {account.phoneNumber}</p>
          <p className="mb-0"><strong>Ngày tạo:</strong> {new Date(account.createdAt).toLocaleString('vi-VN')}</p>
        </Card.Body>
      </Card>
    </div>
  )
}
