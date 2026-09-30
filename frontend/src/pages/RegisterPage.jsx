import { useState } from 'react'
import { Alert, Button, Card, Container, Form } from 'react-bootstrap'
import { Link, useNavigate } from 'react-router-dom'
import axiosClient, { getErrorMessage } from '../api/axiosClient'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ customerName: '', email: '', phoneNumber: '', password: '' })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading] = useState(false)

  const change = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  // Bấm "Đăng ký" → POST /accounts → thành công thì về trang đăng nhập
  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setFieldErrors({})
    setLoading(true)
    try {
      await axiosClient.post('/accounts', form)
      navigate('/login', { state: { registered: true } })
    } catch (err) {
      setError(getErrorMessage(err))
      // Lỗi sai định dạng: backend trả kèm lỗi của từng ô (fieldErrors)
      setFieldErrors(err.response?.data?.fieldErrors || {})
    } finally {
      setLoading(false)
    }
  }

  const field = (name, label, type = 'text', placeholder = '') => (
    <Form.Group className="mb-3">
      <Form.Label>{label}</Form.Label>
      <Form.Control type={type} value={form[name]} onChange={change(name)} placeholder={placeholder}
                    isInvalid={!!fieldErrors[name]} required />
      <Form.Control.Feedback type="invalid">{fieldErrors[name]}</Form.Control.Feedback>
    </Form.Group>
  )

  return (
    <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '100vh' }}>
      <Card style={{ width: 420 }} className="shadow-sm">
        <Card.Body className="p-4">
          <h3 className="text-center mb-4">Đăng ký tài khoản</h3>
          {error && <Alert variant="danger">{error}</Alert>}

          <Form onSubmit={handleSubmit}>
            {field('customerName', 'Họ tên', 'text', 'Nguyễn Văn A')}
            {field('email', 'Email', 'email', 'ban@test.com')}
            {field('phoneNumber', 'Số điện thoại', 'text', '0901234567')}
            {field('password', 'Mật khẩu', 'password', 'Tối thiểu 8 ký tự')}
            <Button type="submit" variant="primary" className="w-100" disabled={loading}>
              {loading ? 'Đang đăng ký...' : 'Đăng ký'}
            </Button>
          </Form>

          <div className="text-center mt-3">
            Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
          </div>
        </Card.Body>
      </Card>
    </Container>
  )
}
