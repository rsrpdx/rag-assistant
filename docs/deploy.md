# 部署到云服务器

项目是六个容器组成的前后端分离应用（Nginx / Spring Boot / MySQL / Redis / MinIO / Qdrant），
推荐直接在一台 Linux 云服务器上用 Docker Compose 部署。

## 0. 需要什么

| 项目 | 建议 |
|------|------|
| 服务器 | Ubuntu 22.04 / 24.04、Debian 或 Alibaba Cloud Linux / CentOS 等 RHEL 系；**2 核 4G 起**（2G 也能跑，脚本会自动加 2G swap，但首次构建偏慢） |
| 磁盘 | 20G 以上（镜像约 2G + 数据） |
| 安全组 | 放行 22、80（配 HTTPS 再放行 443） |
| LLM | 一个 DeepSeek（或其他 OpenAI 兼容接口）的 API Key |
| 域名 | 可选。国内节点绑定域名走 80/443 需要备案；用 IP 访问或选香港/新加坡节点可免备案 |

> 内存说明：JVM + 本地 ONNX Embedding 模型 + MySQL + Qdrant 同时跑，2G 内存会出现构建超时或 OOM 重启，优先选 4G。

## 1. 一键部署

```bash
ssh root@<服务器IP>

# 方式 A：直接拉脚本执行
curl -fsSL https://raw.githubusercontent.com/rsrpdx/rag-assistant/main/scripts/deploy.sh -o deploy.sh
sudo LLM_API_KEY=sk-你的key bash deploy.sh

# 方式 B：先克隆再执行（脚本幂等，可重复跑）
git clone https://github.com/rsrpdx/rag-assistant.git /opt/rag-assistant
cd /opt/rag-assistant
sudo LLM_API_KEY=sk-你的key bash scripts/deploy.sh
```

脚本会做这些事：安装 Docker → 内存不足时加 swap → 拉代码到 `/opt/rag-assistant` →
生成带随机密码的 `.env` → `docker compose up -d --build` → 轮询 `/health` 直到就绪。

> 如果服务器访问 GitHub 很慢，可以先在本地 `git clone` 后打包上传，或在服务器上配好代理再执行。

## 2. 验证

1. 浏览器打开 `http://<服务器IP>`，注册账号 → 新建知识库 → 上传 PDF/Word → 提问。
2. 命令行检查：

```bash
curl -s http://127.0.0.1/health          # 期望输出 OK
cd /opt/rag-assistant && docker compose ps   # 六个服务都应为 Up / healthy
docker compose logs -f backend            # 看后端启动与 LLM 调用日志
```

## 3. 端口与安全

| 端口 | 暴露范围 | 用途 |
|------|----------|------|
| 80 | 公网 | 前端页面 + `/api` 反向代理 |
| 8080 / 3306 / 6379 / 9000 / 6333 / 6334 | 仅 127.0.0.1 | 内部服务与调试 |

默认只把前端 80 端口暴露出去，数据库、Redis、MinIO、Qdrant 都绑定在 127.0.0.1，公网访问不到。
需要看 MinIO / Qdrant 控制台时用 SSH 隧道：

```bash
ssh -L 9001:127.0.0.1:9001 -L 6333:127.0.0.1:6333 root@<服务器IP>
# 然后本地打开 http://localhost:9001（MinIO）与 http://localhost:6333/dashboard（Qdrant）
```

## 4. 绑定域名与 HTTPS

**方式 A（最省事）**：域名解析到服务器 → Cloudflare 开启代理（橙云）→ SSL/TLS 模式选 `Full`，
源站继续只开 80，证书由 Cloudflare 提供。

**方式 B（自签证书）**：用 certbot 申请证书，把 `fullchain.pem` / `privkey.pem` 放到 `./certs`，
再把 `docker/nginx/nginx-prod.conf` 里的 `server_name localhost` 改成你的域名，然后用生产编排启动：

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## 5. 更新与回滚

```bash
cd /opt/rag-assistant
git pull
docker compose up -d --build
```

回滚到某个提交：

```bash
git checkout <commit-id>
docker compose up -d --build
```

## 6. 备份

```bash
# 业务数据（MySQL）
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" rag_assistant' > backup-$(date +%F).sql

# MinIO / Qdrant 数据在 Docker volumes 里
docker volume ls | grep rag-
```

`docker compose down` 不会删数据；**只有 `docker compose down -v` 会连数据一起删**。

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| 构建很慢或超时 | 服务器访问 Maven Central / npm 慢。可给 Docker 配镜像加速，或本地构建镜像后 push 到镜像仓库 |
| 容器反复重启 / OOM | `dmesg | grep -i oom` 确认；升级到 4G，或给 backend 加 `JAVA_TOOL_OPTIONS=-Xmx512m` |
| 问答报错或一直无回复 | 检查 `.env` 的 `LLM_API_KEY`；`docker compose logs -f backend` 看具体报错 |
| 上传大文件 413 | Nginx 的 `client_max_body_size 60M` 与 Spring 的 `max-file-size 50MB` 需同时调整 |
| 80 端口被占用 | 检查 `ss -ltnp 'sport = :80'`；若被 1Panel OpenResty 默认站占用，可停止该容器或在 `.env` 改 `HTTP_PORT=8088` |
| 检索不到内容 | 确认知识库里有文档且状态为解析完成；Qdrant 维度必须是 512，换过模型需删集合重传 |
| 80 端口被占用 | 在 `.env` 里改 `HTTP_PORT=8088`，重新 `docker compose up -d` |
