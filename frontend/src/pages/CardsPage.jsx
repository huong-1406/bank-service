import { useEffect, useState } from 'react'
import { Alert, Badge, Button, Card, Form, Modal, Spinner, Table } from 'react-bootstrap'
import axiosClient, { getErrorMessage } from '../api/axiosClient'
import { formatDate } from '../utils/format'

// Thẻ hợp lệ = ACTIVE và chưa hết hạn — giống Card.isValid() ở backend, ở đây chỉ để hiện nhãn màu
function StatusBadge({ card }) {
  const expired = new Date(card.expiryDate) < new Date(new Date().toDateString())
  if (expired) return <Badge bg="danger">Hết hạn</Badge>
  if (card.status === 'ACTIVE') return <Badge bg="success">ACTIVE</Badge>
  return <Badge bg="secondary">INACTIVE</Badge>
}

export default function CardsPage() {
  const [cards, setCards] = useState(null)
  const [error, setError] = useState('')

  // Hộp tạo thẻ
  const [showCreate, setShowCreate] = useState(false)
  const [newCard, setNewCard] = useState({ cardType: 'DEBIT', expiryDate: '' })
  const [createError, setCreateError] = useState('')

  // Hộp xác nhận xoá
  const [cardToDelete, setCardToDelete] = useState(null)
  const [deleteError, setDeleteError] = useState('')

  // GET /accounts/me/cards
  const loadCards = () => {
    axiosClient.get('/accounts/me/cards')
      .then((res) => setCards(res.data))
      .catch((err) => setError(getErrorMessage(err)))
  }
  useEffect(loadCards, [])

  // POST /accounts/me/cards
  const handleCreate = async (e) => {
    e.preventDefault()
    setCreateError('')
    try {
      await axiosClient.post('/accounts/me/cards', newCard)
      setShowCreate(false)
      setNewCard({ cardType: 'DEBIT', expiryDate: '' })
      loadCards()
    } catch (err) {
      const fieldErrors = err.response?.data?.fieldErrors
      setCreateError(fieldErrors ? Object.values(fieldErrors).join('. ') : getErrorMessage(err))
    }
  }

  // DELETE /cards/{id} — backend chặn nếu thẻ còn giao dịch PENDING
  const handleDelete = async () => {
    setDeleteError('')
    try {
      await axiosClient.delete(`/cards/${cardToDelete.cardId}`)
      setCardToDelete(null)
      loadCards()
    } catch (err) {
      setDeleteError(getErrorMessage(err))
    }
  }

  const closeCreate = () => { setShowCreate(false); setCreateError('') }
  const closeDelete = () => { setCardToDelete(null); setDeleteError('') }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h3 className="mb-0">Thẻ của tôi</h3>
        <Button onClick={() => setShowCreate(true)}>+ Tạo thẻ</Button>
      </div>

      {error && <Alert variant="danger">{error}</Alert>}
      {!cards && !error && <Spinner animation="border" />}

      {cards && (
        <Card className="shadow-sm">
          <Card.Body>
            {cards.length === 0 ? (
              <p className="text-muted mb-0">Bạn chưa có thẻ nào.</p>
            ) : (
              <Table hover className="mb-0 align-middle">
                <thead>
                  <tr><th>Mã thẻ</th><th>Loại</th><th>Hết hạn</th><th>Trạng thái</th><th></th></tr>
                </thead>
                <tbody>
                  {cards.map((card) => (
                    <tr key={card.cardId}>
                      <td>{card.cardId}</td>
                      <td>{card.cardType}</td>
                      <td>{formatDate(card.expiryDate)}</td>
                      <td><StatusBadge card={card} /></td>
                      <td className="text-end">
                        <Button size="sm" variant="outline-danger" onClick={() => setCardToDelete(card)}>Xoá</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card.Body>
        </Card>
      )}

      {/* Hộp tạo thẻ */}
      <Modal show={showCreate} onHide={closeCreate} centered>
        <Form onSubmit={handleCreate}>
          <Modal.Header closeButton><Modal.Title>Tạo thẻ mới</Modal.Title></Modal.Header>
          <Modal.Body>
            {createError && <Alert variant="danger">{createError}</Alert>}
            <Form.Group className="mb-3">
              <Form.Label>Loại thẻ</Form.Label>
              <Form.Select value={newCard.cardType} onChange={(e) => setNewCard({ ...newCard, cardType: e.target.value })}>
                <option value="DEBIT">DEBIT — thẻ ghi nợ</option>
                <option value="CREDIT">CREDIT — thẻ tín dụng</option>
              </Form.Select>
            </Form.Group>
            <Form.Group>
              <Form.Label>Ngày hết hạn</Form.Label>
              <Form.Control type="date" value={newCard.expiryDate} required
                            onChange={(e) => setNewCard({ ...newCard, expiryDate: e.target.value })} />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={closeCreate}>Huỷ</Button>
            <Button type="submit">Tạo</Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* Hộp xác nhận xoá */}
      <Modal show={!!cardToDelete} onHide={closeDelete} centered>
        <Modal.Header closeButton><Modal.Title>Xác nhận xoá thẻ</Modal.Title></Modal.Header>
        <Modal.Body>
          {deleteError && <Alert variant="danger">{deleteError}</Alert>}
          Xoá thẻ số <strong>{cardToDelete?.cardId}</strong>? Thao tác này không hoàn tác được.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={closeDelete}>Huỷ</Button>
          <Button variant="danger" onClick={handleDelete}>Xoá</Button>
        </Modal.Footer>
      </Modal>
    </div>
  )
}
