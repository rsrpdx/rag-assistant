#!/usr/bin/env bash
#
# RAG Assistant 一键部署脚本（Ubuntu / Debian / RHEL 系）
#
# 用法：
#   sudo bash scripts/deploy.sh
#   sudo LLM_API_KEY=sk-xxx bash scripts/deploy.sh
#
# 可用环境变量：
#   APP_DIR        安装目录（默认 /opt/rag-assistant）
#   REPO_URL       仓库地址（默认 GitHub 官方地址）
#   HTTP_PORT      对外端口（默认 80）
#   LLM_BASE_URL   默认 https://api.deepseek.com/v1
#   LLM_API_KEY    LLM 接口密钥
#   LLM_CHAT_MODEL 默认 deepseek-chat
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rag-assistant}"
REPO_URL="${REPO_URL:-https://github.com/rsrpdx/rag-assistant.git}"
HTTP_PORT="${HTTP_PORT:-80}"

log()  { printf '\033[32m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[deploy]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[deploy]\033[0m %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 或 sudo 执行"

log "安装基础依赖"
if command -v apt-get >/dev/null 2>&1; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq git curl openssl ca-certificates
elif command -v dnf >/dev/null 2>&1; then
  dnf install -y git curl openssl ca-certificates
else
  die "不支持的发行版：需要 apt-get 或 dnf"
fi

if ! command -v docker >/dev/null 2>&1; then
  log "安装 Docker Engine（官方脚本）"
  curl -fsSL https://get.docker.com | sh
fi
docker compose version >/dev/null 2>&1 || die "Docker Compose 插件不可用，请重装 Docker"

if [ ! -f /swapfile ]; then
  mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
  if [ "$mem_mb" -lt 3800 ]; then
    log "内存 ${mem_mb}MB，创建 2GB swap 防止构建/运行 OOM"
    fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
fi

log "准备代码目录 ${APP_DIR}"
if [ -d "${APP_DIR}/.git" ]; then
  git -C "${APP_DIR}" pull --ff-only
else
  mkdir -p "$(dirname "${APP_DIR}")"
  git clone --depth 1 "${REPO_URL}" "${APP_DIR}"
fi
cd "${APP_DIR}"

if [ ! -f .env ]; then
  log "生成 .env（随机生成数据库/Redis/MinIO/JWT 密钥）"
  cat > .env <<EOF
LLM_BASE_URL=${LLM_BASE_URL:-https://api.deepseek.com/v1}
LLM_API_KEY=${LLM_API_KEY:-}
LLM_CHAT_MODEL=${LLM_CHAT_MODEL:-deepseek-chat}
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 32)
MYSQL_USER=rag_user
MYSQL_PASSWORD=$(openssl rand -base64 32)
REDIS_PASSWORD=$(openssl rand -base64 32)
MINIO_ROOT_USER=ragadmin
MINIO_ROOT_PASSWORD=$(openssl rand -base64 32)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
HTTP_PORT=${HTTP_PORT}
EOF
  chmod 600 .env
else
  log ".env 已存在，保留原配置"
fi

if ! grep -q '^LLM_API_KEY=..*' .env; then
  warn "LLM_API_KEY 还没填，问答功能会失败：编辑 ${APP_DIR}/.env 后执行 docker compose up -d"
fi

log "构建并启动六个服务（首次约 5~10 分钟，取决于服务器网络）"
COMPOSE_PARALLEL_LIMIT=1 docker compose up -d --build

log "等待服务就绪"
ok=0
for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT}/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 5
done
[ "$ok" -eq 1 ] || die "健康检查未通过：cd ${APP_DIR} && docker compose logs -f backend"

ip=$(curl -fsS -m 5 https://api.ipify.org 2>/dev/null || echo "<服务器公网IP>")
log "部署完成：http://${ip}:${HTTP_PORT}"
docker compose ps
