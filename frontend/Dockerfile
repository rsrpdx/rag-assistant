# ===================== Build Stage =====================
FROM node:20-alpine AS builder

WORKDIR /app

# context 是仓库根目录，路径需要带上 frontend/ 前缀
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ===================== Serve Stage =====================
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
ARG NGINX_CONFIG=docker/nginx/nginx.conf
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]