import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import Layout from './components/Layout'
import PrivateRoute from './components/PrivateRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import ProfilePage from './pages/ProfilePage'
import CardsPage from './pages/CardsPage'

// Danh sách các trang. Trang nằm trong PrivateRoute thì phải đăng nhập mới vào được
export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<PrivateRoute><Layout /></PrivateRoute>}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/cards" element={<CardsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
