import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Modal } from 'react-bootstrap'
import { useNavigate } from 'react-router-dom'
import axiosClient, { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../context/AuthContext'

export default function ProfilePage() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', phoneNumber: '' })
  const [message, setMessage] = useState(null)       // { type: 'success' | 'danger', text }
  const [fieldErrors, setFieldErrors] = useState({})
  const [showDelete, setShowDelete] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  // Điền sẵn email, SĐT hiện tại
  useEffect(() => {
    axiosClient.get('/accounts/me')
      .then((res) => setForm({ email: res.data.email, phoneNumber: res.data.phoneNumber }))
      .catch((err) => setMessage({ type: 'danger', text: getErrorMessage(err) }))
  }, [])

  // Sửa email / SĐT → PUT /accounts/me (đề bài chỉ cho sửa 2 trường này)
  const handleUpdate = async (e) => {
    e.preventDefault()
    setMessage(null)
    setFieldErrors({})
    try {
      await axiosClient.put('/accounts/me', form)
      setMessage({ type: 'success', text: 'Cập nhật thành công' })
    } catch (err) {
      setMessage({ type: 'danger', text: getErrorMessage(err) })
      setFieldErrors(err.response?.data?.fieldErrors || {})
    }
  }

  // Xoá tài khoản → DELETE /accounts/me. Backend chặn nếu còn thẻ hoặc còn tiền
  const handleDelete = async () => {
    setDeleteError('')
    try {
      await axiosClient.delete('/accounts/me')
      logout()
      navigate('/login')
    } catch (err) {
      setDeleteError(getErrorMessage(err))
    }
  }

  return (
    <div style={{ maxWidth: 520 }}>
      <h3 className="mb-4">Tài khoản</h3>

      <Card className="shadow-sm mb-4">
        <Card.Header>Sửa thông tin</Card.Header>
        <Card.Body>
          {message && <Alert variant={message.type}>{message.text}</Alert>}
          <Form onSubmit={handleUpdate}>
            <Form.Group className="mb-3">
              <Form.Label>Email</Form.Label>
              <Form.Control type="email" value={form.email} isInvalid={!!fieldErrors.email}
                            onChange={(e) => setForm({ ...form, email: e.target.value })} />
              <Form.Control.Feedback type="invalid">{fieldErrors.email}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group className="mb-3">
              <Form.Label>Số điện thoại</Form.Label>
              <Form.Control value={form.phoneNumber} isInvalid={!!fieldErrors.phoneNumber}
                            onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} />
              <Form.Control.Feedback type="invalid">{fieldErrors.phoneNumber}</Form.Control.Feedback>
            </Form.Group>
            <Button type="submit">Lưu thay đổi</Button>
          </Form>
        </Card.Body>
      </Card>

      <Card border="danger" className="shadow-sm">
        <Card.Header className="text-danger">Xoá tài khoản</Card.Header>
        <Card.Body>
          <p className="text-muted mb-3">Chỉ xoá được khi không còn thẻ và số dư bằng 0.</p>
          <Button variant="outline-danger" onClick={() => setShowDelete(true)}>Xoá tài khoản</Button>
        </Card.Body>
      </Card>

      {/* Hỏi xác nhận trước khi xoá */}
      <Modal show={showDelete} onHide={() => { setShowDelete(false); setDeleteError('') }} centered>
        <Modal.Header closeButton><Modal.Title>Xác nhận xoá tài khoản</Modal.Title></Modal.Header>
        <Modal.Body>
          {deleteError && <Alert variant="danger">{deleteError}</Alert>}
          Bạn chắc chắn muốn xoá tài khoản? Thao tác này không hoàn tác được.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => { setShowDelete(false); setDeleteError('') }}>Huỷ</Button>
          <Button variant="danger" onClick={handleDelete}>Xoá</Button>
        </Modal.Footer>
      </Modal>
    </div>
  )
}
