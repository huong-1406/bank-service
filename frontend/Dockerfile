# Bước 1: dùng Node build code React thành file web tĩnh (thư mục dist/)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite gắn địa chỉ API vào lúc BUILD, không phải lúc chạy → truyền bằng build arg.
# Trình duyệt chạy trên máy người dùng nên gọi localhost:8080, không phải tên container
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Bước 2: nginx phục vụ các file web đã build — image nhẹ, không cần Node lúc chạy
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
