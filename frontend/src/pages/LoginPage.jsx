import { useState } from 'react'
import { Alert, Button, Card, Container, Form } from 'react-bootstrap'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import axiosClient, { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
  const { token, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // Đã đăng nhập rồi thì không cần ở trang này
  if (token) return <Navigate to="/dashboard" replace />

  // Bấm "Đăng nhập" → POST /auth/login → lưu token → sang Dashboard
  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await axiosClient.post('/auth/login', { email, password })
      login(res.data.token)
      navigate('/dashboard')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '100vh' }}>
      <Card style={{ width: 400 }} className="shadow-sm">
        <Card.Body className="p-4">
          <h3 className="text-center mb-4">Đăng nhập</h3>

          {location.state?.registered && !error && (
            <Alert variant="success">Đăng ký thành công, mời bạn đăng nhập.</Alert>
          )}
          {error && <Alert variant="danger">{error}</Alert>}

          <Form onSubmit={handleSubmit}>
            <Form.Group className="mb-3">
              <Form.Label>Email</Form.Label>
              <Form.Control type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                            placeholder="a@test.com" required />
            </Form.Group>
            <Form.Group className="mb-4">
              <Form.Label>Mật khẩu</Form.Label>
              <Form.Control type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </Form.Group>
            <Button type="submit" variant="primary" className="w-100" disabled={loading}>
              {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
            </Button>
          </Form>

          <div className="text-center mt-3">
            Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
          </div>
        </Card.Body>
      </Card>
    </Container>
  )
}
