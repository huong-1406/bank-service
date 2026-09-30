import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Chưa đăng nhập thì đẩy về /login. Chỉ là chặn ở giao diện — bảo mật thật nằm ở backend (không token → 401)
export default function PrivateRoute({ children }) {
  const { token } = useAuth()
  return token ? children : <Navigate to="/login" replace />
}
