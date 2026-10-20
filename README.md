# AI 知识库问答平台（RAG Assistant）

[![CI](https://github.com/rsrpdx/rag-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/rsrpdx/rag-assistant/actions/workflows/ci.yml)

基于 **Spring Boot 3 + LangChain4j + Qdrant** 的检索增强生成（RAG）问答平台。
文档上传 → 解析 → 分块 → 本地向量化 → 语义检索 → LLM 生成，回答经 **SSE** 流式输出。

线上环境：<https://fearless.website>（HTTPS，含自动续期证书）

---

## 一、功能特性

- **知识库管理**：多知识库、分类筛选、封面、文档数与切片数统计
- **文档管理**：PDF / Word / Markdown 上传，异步解析与切片，失败重试，在线预览与下载
- **智能问答**：基于知识库检索增强，回答带引用来源（docId / docName / 相似度得分）
- **流式输出**：SSE 逐 token 推送，前端 Markdown 实时渲染
- **用户体系**：注册登录、JWT 鉴权、Redis 令牌黑名单、头像上传、修改密码
- **仪表盘**：知识库/文档/切片/会话统计与最近动态
- **工程化**：六服务 Docker Compose 编排、Nginx 静态托管与反向代理、GitHub Actions CI

## 二、技术栈

| 层次 | 选型 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4、MyBatis-Plus、LangChain4j、Spring Security + JWT |
| 前端 | Vue 3、TypeScript、Vite 6、Element Plus、Pinia |
| 存储 | MySQL 8（业务数据）、Redis（缓存/黑名单）、MinIO（原始文件）、Qdrant（向量） |
| 文档解析 | Apache PDFBox（PDF）、Apache POI（Word）、Markdown 直读 |
| 向量化 | 本地 ONNX 模型 `bge-small-zh-v1.5`，512 维，进程内推理，无需外部 Embedding API |
| LLM | 任何兼容 OpenAI API 的服务（DeepSeek / vLLM / Ollama / Azure OpenAI） |
| 部署 | Docker Compose 六服务、Nginx、Let's Encrypt（acme.sh）、GitHub Actions |

## 三、系统架构

```
                       HTTPS 443 / HTTP 80
                              │
                    ┌─────────▼──────────┐
                    │  Nginx (frontend)  │  静态资源
                    │  /api  → backend   │  反向代理
                    │  /rag-files → minio│  预签名文件
                    └─────────┬──────────┘
                              │
                    ┌─────────▼──────────┐
                    │ Spring Boot 3      │
                    │ RAG / Chat / Auth  │
                    └──┬────┬────┬────┬──┘
                       │    │    │    │
                    MySQL Redis MinIO Qdrant
                    :3306 :6379 :9000 :6333/6334
```

六个容器同处 `rag-network` 桥接网络，只有 Nginx 对外暴露 80/443，其余端口均绑定 `127.0.0.1`。

## 四、RAG 实现细节

| 阶段 | 实现 | 关键参数 |
|------|------|----------|
| 文档解析 | PDFBox / POI / Markdown 直读 | 支持 PDF、DOC/DOCX、MD、TXT |
| 文本分块 | 自定义滑动窗口分块（保留上下文重叠） | 窗口 500 字符，重叠 50 字符 |
| 向量嵌入 | `BgeSmallZhV15EmbeddingModel`（ONNX Runtime 进程内推理） | 512 维，模型随 jar 打包 |
| 向量存储 | Qdrant，余弦距离 | collection：`rag_knowledge` |
| 语义检索 | top-k 相似度检索 + 阈值过滤 | top-k = 5，min-score = 0.4 |
| 答案生成 | LangChain4j 编排 OpenAI 兼容模型 | temperature 0.7，max-tokens 2048 |
| 输出 | `SseEmitter` 流式推送 + `done` 事件携带引用 | 前端 `TextDecoder('utf-8')` 增量渲染 |

> 分块未使用 LangChain4j 内置分块器：内置实现按 Token 切分且窗口边界处理不透明，自定义实现更便于控制中文标点与重叠长度。

## 五、快速开始（Docker）

```bash
# 1. 克隆项目
git clone https://github.com/rsrpdx/rag-assistant.git
cd rag-assistant

# 2. 准备环境变量（务必替换成自己的随机密码/密钥）
cp .env.example .env
#   JWT_SECRET：      openssl rand -base64 64
#   MySQL/Redis/MinIO：openssl rand -base64 32

# 3. 启动六个服务
docker compose up -d

# 4. 查看启动日志
docker compose logs -f backend
```

访问：

| 服务 | 地址 |
|------|------|
| 前端页面 | <http://localhost> |
| 后端 API | <http://localhost:8080/api/v1> |
| MinIO 控制台 | <http://localhost:9001>（账号见 `.env`） |
| Qdrant 面板 | <http://localhost:6333/dashboard> |

## 六、本地开发

```bash
# 后端：默认连本机 MySQL/Redis/MinIO/Qdrant，端口 8081
cd backend && mvn spring-boot:run

# 前端：dev server 将 /api 代理到 http://localhost:8081
cd frontend && npm install && npm run dev
```

需要本地已启动 MySQL(3306)、Redis(6379)、MinIO(9000)、Qdrant(6333/6334)，或在 `.env` 中改指向容器。

## 七、生产部署

服务器建议 **2 核 2G 起步**。本项目实测在 1.8G 内存 + 2G swap 的阿里云 ECS 上稳定运行（六服务常驻约 1.6G）。

```bash
git clone https://github.com/rsrpdx/rag-assistant.git /opt/rag-assistant
cd /opt/rag-assistant
cp .env.example .env && vim .env          # 填写数据库/Redis/MinIO 密码、JWT_SECRET、LLM_API_KEY

# 申请证书（acme.sh 示例，Nginx 容器挂载 ./certs）
# 证书就位后使用生产编排（HTTPS + 资源限制 + 日志轮转）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

生产编排的关键点：

- **JVM 内存**：`JAVA_TOOL_OPTIONS="-Xmx640m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -XX:ActiveProcessorCount=2"`。容器内存上限 1200M 时 JVM 默认只分配 25% 堆，加载 ONNX 模型会直接 OOM，必须显式指定。
- **文件访问**：MinIO 预签名客户端显式指定 `region("us-east-1")`，避免容器内回调公网域名，生成 `https://你的域名/rag-files/...?X-Amz-Signature=...`，由 Nginx 反代到 MinIO，杜绝 HTTPS 页面加载 HTTP 资源的混合内容问题。
- **证书续期**：acme.sh 定时任务 + Nginx 挂载 `./certs`，续期后 `docker exec rag-frontend nginx -s reload`。

完整步骤（安全组、域名解析、HTTPS、备份、排障）见 [docs/deploy.md](docs/deploy.md)。

## 八、主要配置项

| 变量 | 说明 | 示例 |
|------|------|------|
| `LLM_BASE_URL` | OpenAI 兼容接口地址 | `https://api.deepseek.com/v1` |
| `LLM_API_KEY` | LLM 密钥（仅写入服务器 `.env`） | `sk-****` |
| `LLM_CHAT_MODEL` | 模型名 | `deepseek-chat` |
| `MINIO_PUBLIC_ENDPOINT` | 文件对外访问基地址 | `https://fearless.website` |
| `JWT_SECRET` | JWT 签名密钥 | `openssl rand -base64 64` |
| `HTTP_PORT` / `HTTPS_PORT` | Nginx 对外端口 | `80` / `443` |

> `docker compose` 的变量优先级是 **Shell 环境变量 > `.env` 文件**。若发现配置不生效，先 `echo $LLM_API_KEY` 确认 Shell 里没有残留的旧值。

## 九、API 一览

| 模块 | 接口 |
|------|------|
| 认证 | `POST /api/v1/auth/register`、`/login`、`/logout`、`/refresh`、`GET /api/v1/auth/me` |
| 知识库 | `GET/POST /api/v1/kb`、`GET/PUT/DELETE /api/v1/kb/{id}`、`GET /api/v1/kb/{id}/stats` |
| 文档 | `POST /api/v1/doc/upload`、`GET /api/v1/doc/{kbId}`、`GET /api/v1/doc/detail/{id}`、`DELETE /api/v1/doc/{id}`、`POST /api/v1/doc/{id}/retry`、`GET /api/v1/doc/{id}/download`、`GET /api/v1/doc/{id}/preview-url` |
| 会话 | `GET/POST /api/v1/chat/session`、`DELETE /api/v1/chat/session/{id}`、`GET /api/v1/chat/session/{id}/messages` |
| 问答 | `POST /api/v1/chat/session/{id}/ask`、`GET /api/v1/chat/session/{id}/ask-stream`（SSE） |
| 仪表盘 | `GET /api/v1/dashboard/stats`、`/recent-activity`、`/search` |
| 用户 | `GET/PUT /api/v1/user/profile`、`POST /api/v1/user/avatar`、`PUT /api/v1/user/password` |

统一响应结构：`{ "code": 200, "message": "操作成功", "data": ..., "timestamp": ... }`。
业务错误码：`4011` 用户名或密码错误、`4003` 未登录、`4004` 无权限、`4005` 资源不存在、`5001` AI 服务不可用。

## 十、测试与 CI

```bash
cd backend && mvn -B test          # 16 个用例（2 个依赖外部 Qdrant 的用例在未启动时自动跳过）
cd backend && mvn -B package -DskipTests
cd frontend && npm ci && npm run build
```

- `JwtUtilTest`、`TextSplitterServiceTest`、`DocumentParserServiceTest`：纯单元测试，无外部依赖
- `EmbeddingQualityTest`：对比哈希向量化与 `bge-small-zh` 在中文改写提问下的召回效果
- `IngestionPipelineVerifyTest`、`QdrantRetrievalTest`：端到端入库与检索验证（Qdrant 未启动时跳过）

CI（`.github/workflows/ci.yml`）在每次 push / PR 时执行后端测试与前端构建。

## 十一、常用命令

```bash
docker compose up -d                    # 启动全部
docker compose down                     # 停止全部
docker compose logs -f backend          # 查看日志
docker compose restart backend          # 重启后端
docker compose build backend && docker compose up -d   # 代码变更后重建
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d   # 生产编排
docker compose down -v                  # 清理数据（危险）
```

## 十二、目录结构

```
rag-assistant/
├── .github/workflows/ci.yml      # CI：后端测试 + 前端构建
├── backend/                      # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/rag/assistant/
│       ├── common/               # 统一响应、异常、JWT 工具
│       ├── module/rag/           # 解析、分块、向量化、检索（RAG 主链路）
│       ├── module/chat/          # 会话管理与 SSE 流式问答
│       ├── module/kb/            # 知识库
│       ├── module/document/      # 文档
│       ├── module/dashboard/     # 仪表盘
│       └── thirdparty/           # MinIO 等外部依赖适配
├── frontend/                     # Vue 3 前端
├── docker/
│   ├── mysql/init.sql            # 数据库初始化
│   └── nginx/                    # nginx.conf / nginx-prod.conf
├── docs/                         # 架构、数据库、RAG、UI、部署文档
├── docker-compose.yml            # 六服务编排
├── docker-compose.prod.yml       # 生产覆盖（HTTPS、资源限制、日志轮转）
├── .env.example
└── README.md
```

## 十三、常见问题

**1. 页面能打开，但接口 502**
后端没起来。`docker ps -a --filter name=rag-backend` 看是否在重启循环，再看 `docker logs --tail 100 rag-backend`。最常见是内存不足导致 JVM 启动失败。

**2. 启动日志出现 `OutOfMemoryError: Java heap space`（`OnnxBertBiEncoder.loadModel`）**
容器内存限制下 JVM 默认堆只有上限的 25%。显式设置 `JAVA_TOOL_OPTIONS=-Xmx640m`，并保证容器上限不低于 1G。

**3. 文档/头像图片 403 或浏览器报 Mixed Content**
说明拿到了未签名的 `http://minio:9000/...` 地址。检查 `MINIO_PUBLIC_ENDPOINT` 是否为 HTTPS 域名，以及后端预签名是否成功（日志中 `MinIO presign failed`）。

**4. 登录返回 401 / 4011**
`4011` 表示用户名或密码错误；`4003` 表示未登录或令牌失效。真实 Key/密码问题请看后端日志，不会返回给前端。

**5. 改了 `.env` 不生效**
Shell 中残留的同名环境变量优先级更高；`unset` 后重新 `up -d --force-recreate`。

## 十四、安全说明

- 真实密钥只写在服务器 `.env`（已被 `.gitignore` 忽略），仓库仅保留 `.env.example`。
- 所有密码使用随机值：`openssl rand -base64 32`；JWT 用 `openssl rand -base64 64`。
- 密钥一旦进入版本库或聊天记录，应立即吊销重建——删除文件不足以补救。
- 生产环境仅暴露 80/443，数据库、Redis、MinIO、Qdrant 端口绑定 `127.0.0.1`。
- 后端异常统一收敛：认证失败返回 401，系统异常返回 500 且不向前端透出内部错误信息。

## 十五、License

仅用于学习与个人作品展示，未授权商用。