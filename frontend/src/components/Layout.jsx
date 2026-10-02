import { Nav } from 'react-bootstrap'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Khung chung sau khi đăng nhập: menu tối bên trái (theo Figma), nội dung bên phải
export default function Layout() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="d-flex" style={{ minHeight: '100vh' }}>
      <aside className="sidebar d-flex flex-column p-3">
        <h5 className="text-white mb-4">🏦 Bank Service</h5>
        <Nav className="flex-column flex-grow-1">
          <Nav.Link as={NavLink} to="/dashboard">Tổng quan</Nav.Link>
          <Nav.Link as={NavLink} to="/profile">Tài khoản</Nav.Link>
          <Nav.Link as={NavLink} to="/cards">Thẻ</Nav.Link>
        </Nav>
        <button className="btn btn-outline-light btn-sm" onClick={handleLogout}>Đăng xuất</button>
      </aside>
      <main className="flex-grow-1 p-4 bg-light">
        <Outlet />
      </main>
    </div>
  )
}
