# AI 知识库问答平台（RAG Assistant）

[![CI](https://github.com/rsrpdx/rag-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/rsrpdx/rag-assistant/actions/workflows/ci.yml)

基于 **Spring Boot 3 + LangChain4j + Qdrant** 的检索增强生成（RAG）问答平台：
文档上传 → 解析 → 分块 → 本地向量化 → 语义检索 → LLM 生成，回答经 SSE 流式输出。

线上环境：<http://fearless.website>（IP：`43.108.121.102`）

---

## 一、技术栈

| 层次 | 选型 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4、MyBatis-Plus、LangChain4j、Spring Security + JWT |
| 前端 | Vue 3、TypeScript、Vite 6、Element Plus、Pinia |
| 存储 | MySQL 8（业务数据）、Redis（缓存）、MinIO（原始文件）、Qdrant（向量） |
| 文档解析 | Apache PDFBox（PDF）、Apache POI（Word）、Markdown 直读 |
| 向量化 | 本地 ONNX 模型 bge-small-zh-v15，512 维，进程内推理 |
| LLM | 任何兼容 OpenAI API 的服务（DeepSeek / vLLM / Ollama / Azure OpenAI） |
| 部署 | Docker Compose 六服务编排、Nginx 静态托管与反向代理、GitHub Actions |

## 二、环境要求

- Docker Engine >= 24.0、Docker Compose >= 2.20
- 本地开发另需：JDK 21、Maven 3.9、Node.js 20+

## 三、快速启动（Docker）

```bash
# 1. 克隆项目
git clone https://github.com/rsrpdx/rag-assistant.git
cd rag-assistant

# 2. 准备环境变量（务必替换成自己的随机密码/密钥）
cp .env.example .env

# 3. 启动六个服务
docker compose up -d

# 4. 查看启动日志
docker compose logs -f

# 5. 访问
# 前端:       http://localhost
# 后端 API:   http://localhost:8080/api/v1
# MinIO 控制台: http://localhost:9001   （账号密码见 .env 的 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD）
# Qdrant 面板:  http://localhost:6333/dashboard
```

## 四、部署到云服务器

一台 2C4G 的 Linux 服务器即可（Ubuntu / Debian / Alibaba Cloud Linux 等）。脚本会安装 Docker、生成随机密码、拉起六个服务并轮询健康检查：

```bash
git clone https://github.com/rsrpdx/rag-assistant.git /opt/rag-assistant
cd /opt/rag-assistant
sudo LLM_API_KEY=sk-你的key bash scripts/deploy.sh
```

完整步骤（安全组、域名与 HTTPS、备份、排障）见 [docs/deploy.md](docs/deploy.md)。

## 五、本地开发

```bash
# 后端（默认连本机 MySQL/Redis/MinIO/Qdrant，端口见 application.yml）
cd backend && mvn spring-boot:run

# 前端（dev server 会把 /api 代理到 http://localhost:8081）
cd frontend && npm install && npm run dev
```

## 六、服务架构

```
┌──────────────────────────────────────────────────────┐
│                    Nginx (80)                        │
│                    rag-frontend                      │
├──────────────────────────────────────────────────────┤
│                    Backend (8080)                    │
│                    rag-backend                       │
├──────────┬──────────┬──────────┬─────────────────────┤
│  MySQL   │  Redis   │  MinIO   │  Qdrant             │
│   :3306  │  :6379   │ :9000    │  :6333 / :6334      │
├──────────┴──────────┴──────────┴─────────────────────┤
│                 rag-network (bridge)                 │
└──────────────────────────────────────────────────────┘
```

## 七、数据持久化

所有数据存放在 Docker volumes 中，删除容器不会丢数据：

| 服务 | Volume | 容器内路径 |
|------|--------|------------|
| MySQL | mysql-data | /var/lib/mysql |
| Redis | redis-data | /data |
| MinIO | minio-data | /bitnami/minio/data |
| Qdrant | qdrant-data | /qdrant/storage |

## 八、LLM 配置

```bash
LLM_BASE_URL=https://api.deepseek.com/v1   # 或 http://192.168.1.100:8000/v1（vLLM/Ollama）
LLM_API_KEY=sk-your-key
LLM_CHAT_MODEL=deepseek-chat
```

### 向量化与检索参数

向量化不走外部接口，使用本地 ONNX 模型 `bge-small-zh-v15`（512 维，进程内推理），
因此无需任何 Embedding API 即可完成入库与检索。相关参数在 `application.yml` 的 `rag` 节点：

```yaml
rag:
  qdrant:
    dimension: 512        # 必须与 Embedding 模型输出维度一致，不一致时启动日志会报错提示
  doc:
    chunk:
      max-size: 500       # 分块窗口（字符）
      overlap: 50         # 相邻分块重叠，避免答案在切点被截断
  search:
    max-results: 5        # 每次召回的最大片段数
    min-score: 0.4        # 余弦相似度门槛，低于该值的片段不进入 LLM 上下文
```

> 说明：分块由 `TextSplitterService` 自己实现滑动窗口。实测 langchain4j 1.0.0-beta1 的
> `DocumentByCharacterSplitter` / `DocumentSplitters.recursive` 都会忽略 overlap 参数（相邻分块重叠恒为 0），
> 所以没有直接使用其内置分块器。

## 九、构建与测试

```bash
# 后端：编译 + 单元测试（16 个用例）
cd backend && mvn -B test

# 后端：打包（生成 target/rag-assistant-1.0.0.jar）
cd backend && mvn -B package -DskipTests

# 前端：类型检查 + 生产构建
cd frontend && npm ci && npm run build
```

`mvn test` 中包含：

- `JwtUtilTest`、`TextSplitterServiceTest`、`DocumentParserServiceTest`：纯单元测试，无外部依赖；
- `EmbeddingQualityTest`：对比哈希向量化与 bge-small-zh 的中文改写提问召回效果（首次运行会下载 ONNX 模型）；
- `IngestionPipelineVerifyTest`、`QdrantRetrievalTest`：端到端入库检索验证，Qdrant 未启动时自动跳过。

CI（`.github/workflows/ci.yml`）在每次 push / PR 时执行后端 `mvn -B test` 与前端 `npm run build`。

## 十、常用命令

```bash
docker compose up -d                    # 启动全部
docker compose down                     # 停止全部
docker compose restart backend          # 重启某个服务
docker compose logs -f backend          # 查看日志
docker compose build backend && docker compose up -d   # 代码变更后重新构建
docker compose down -v                  # 清理数据（危险，会删除所有数据）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d   # 生产配置（SSL + 资源限制）
```

## 十一、访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost | RAG Assistant UI |
| 后端 API | http://localhost:8080/api/v1 | RESTful API |
| MinIO 控制台 | http://localhost:9001 | 文件管理，账号见 `.env` |
| Qdrant 面板 | http://localhost:6333/dashboard | 向量数据管理 |

## 十二、安全说明

- `.env` 只保存在本机，已在 `.gitignore` 中忽略；仓库里只提供 `.env.example` 模板。
- 所有密码/密钥请使用随机值：`openssl rand -base64 32`（JWT 用 `-base64 64`）。
- 如果密钥曾经进过版本库，请立即在服务端轮换（吊销重建），删文件不足以补救。
- 生产部署建议通过 `docker-compose.prod.yml` 挂载 `./certs` 开启 HTTPS，并对公网只暴露 80/443。

## 十三、项目结构

```
rag-assistant/
├── .github/workflows/ci.yml      # CI：后端测试 + 前端构建
├── backend/                      # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/rag/assistant/
│       ├── common/               # 通用返回、异常、JWT 工具
│       ├── module/rag/           # 解析、分块、向量化、检索（RAG 主链路）
│       ├── module/chat/          # 会话与流式问答
│       └── thirdparty/           # MinIO、LLM 等外部依赖适配
│   └── src/test/                 # 单元测试 + 端到端验证
├── frontend/                     # Vue 3 前端
│   ├── Dockerfile
│   └── src/
├── docker/
│   ├── mysql/init.sql            # 数据库初始化
│   └── nginx/nginx.conf          # Nginx 配置（含 nginx-prod.conf）
├── docs/design/                  # 架构 / 数据库 / RAG / UI 设计文档
├── docker-compose.yml            # 六服务编排
├── docker-compose.prod.yml       # 生产覆盖配置
├── .env.example                  # 环境变量模板
└── README.md
```

## 十四、从旧版本升级

1. 旧版本使用 384 维向量，需先删除旧集合再重新上传文档：
   `curl -X DELETE http://localhost:6333/collections/rag_knowledge`
2. 若数据库由旧版 `init.sql` 创建，需补上知识库分类列：
   `ALTER TABLE knowledge_base ADD COLUMN category VARCHAR(50) DEFAULT NULL COMMENT '知识库分类' AFTER description;`
3. 旧版本分块未真正重叠（窗口边界会截断答案），升级后建议重新上传文档以生成带重叠的分块。
