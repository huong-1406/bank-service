import axios from 'axios'

// Một chỗ duy nhất gọi API: tự gắn token, tự xử lý hết hạn đăng nhập
const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
})

// Trước mỗi request: gắn token (nếu có) vào header Authorization
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Sau mỗi response: gặp 401 (token hết hạn / sai) thì xoá token và về trang đăng nhập.
// Bỏ qua chính API đăng nhập, vì sai mật khẩu cũng trả 401 nhưng phải hiện lỗi tại chỗ
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLogin = error.config?.url?.includes('/auth/login')
    if (error.response?.status === 401 && !isLogin) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// Lấy câu lỗi backend trả về (định dạng chung của GlobalExceptionHandler)
export function getErrorMessage(error) {
  return error.response?.data?.message || 'Không kết nối được máy chủ'
}

export default axiosClient
