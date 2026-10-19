# AI知识库问答平台（RAG Assistant）— 系统设计方案

> 面向校招面试的企业级项目，预计7天开发完成

---

## 一、项目整体架构图

```
+-----------------------------------------------------------------------+
|                          +-----------+                                 |
|                          |   Nginx   |                                 |
|                          | 反向代理   |                                 |
|                          +-----+-----+                                 |
|                                |                                        |
|                +---------------+---------------+                        |
|                |               |               |                        |
|          +-----v-----+  +------v------+  +----v-----+                  |
|          | Vue3 SPA  |  |  Spring Boot|  |  MinIO   |                  |
|          | (Frontend)|  |  (Backend)  |  | 对象存储  |                  |
|          |  :5173     |  |  :8080      |  |  :9000   |                  |
|          +-----+-----+  +------+------+  +----+-----+                  |
|                |               |               |                        |
|                +-------+-------+               |                        |
|                        |                       |                        |
|                 +------v------+         +------v------+                 |
|                 |   Redis     |         |   MySQL8    |                 |
|                 |  缓存/令牌   |         |  业务数据库  |                 |
|                 +-------------+         +-------------+                 |
|                                                                         |
|     +-------------------------------------------------------+          |
|     |                    AI 服务层                          |          |
|     |  +------------+  +------------+  +------------------+ |          |
|     |  |LangChain4j |  |  Qdrant    |  | OpenAI 兼容接口  | |          |
|     |  | 编排引擎    |  | 向量数据库  |  |(LLM+Embedding) | |          |
|     |  +------------+  +------------+  +------------------+ |          |
|     +-------------------------------------------------------+          |
|                                                                         |
|     +----------------------------------------------------+             |
|     |               Docker Compose 编排                    |             |
|     |  mysql|redis|qdrant|minio|backend|frontend|nginx     |             |
|     +----------------------------------------------------+             |
+-----------------------------------------------------------------------+
```

### 分层说明

| 层级 | 组件 | 职责 |
|------|------|------|
| 接入层 | Nginx | 反向代理、静态资源服务、CORS、限流 |
| 前端层 | Vue3 + Element Plus | SPA单页应用、SSE流式渲染 |
| 网关/安全层 | Spring Security + JWT | 认证授权、接口鉴权、RBAC |
| 业务层 | Spring Boot Controller/Service | RESTful API、文件上传、PDF解析、问答对话 |
| AI编排层 | LangChain4j | 文档解析->切片->向量化->检索->LLM问答编排 |
| 数据层 | MySQL + Redis + Qdrant + MinIO | 结构化/缓存/向量/文件存储 |
| 基础设施 | Docker Compose | 全容器化部署 |

---

## 二、系统功能模块图

```
+-------------------------------------------------------------------+
|                     AI知识库问答平台                                |
|                     RAG Assistant                                  |
+-------------------------------------------------------------------+
|                                                                    |
|  +--------------+  +--------------+  +--------------+              |
|  |  用户管理模块  |  |  知识库模块   |  |  文档管理模块  |             |
|  |              |  |              |  |              |             |
|  | * 注册/登录   |  | * 创建知识库  |  | * PDF上传     |             |
|  | * JWT令牌    |  | * 知识库列表  |  | * 文档列表    |             |
|  | * 个人信息    |  | * 知识库权限  |  | * 文档删除    |             |
|  | * 退出登录    |  | * 知识库删除  |  | * 解析状态查看 |             |
|  +--------------+  +--------------+  +--------------+              |
|                                                                    |
|  +--------------+  +--------------+  +--------------+              |
|  |  文档解析模块  |  |  智能问答模块  |  |  系统管理模块  |             |
|  |              |  |              |  |              |             |
|  | * PDF文本提取 |  | * 流式问答SSE |  | * 令牌用量    |             |
|  | * 文本分片    |  | * 历史对话    |  | * 文档统计    |             |
|  | * 向量化存储  |  | * 上下文记忆  |  | * 接口性能    |             |
|  | * 进度展示    |  | * 来源追溯    |  | * 日志查看    |             |
|  +--------------+  +--------------+  +--------------+              |
|                                                                    |
+-------------------------------------------------------------------+
```

### 核心业务流程

```
用户上传PDF -> 解析文本 -> 文本切片 -> Embedding向量化 -> 存入Qdrant
                                                                     |
用户提问 -> Embedding向量化查询 -> Qdrant相似度检索 -> 构建Prompt -> LLM回答 -> SSE推送前端
```

---

## 三、数据库ER图

### 3.1 概念模型

```
+--------------+       +------------------+       +----------------+
|    User      |       |   KnowledgeBase   |       |   Document     |
|--------------|       |------------------|       |----------------|
| id (PK)      |--1:N--| id (PK)          |--1:N--| id (PK)        |
| username     |       | name             |       | filename       |
| password     |       | description      |       | original_name  |
| email        |       | user_id (FK)     |       | file_size      |
| avatar       |       | created_at       |       | file_type      |
| created_at   |       | updated_at       |       | kb_id (FK)     |
| updated_at   |       | status           |       | user_id (FK)   |
|              |       |                  |       | status         |
|              |       |                  |       | chunk_count    |
|              |       |                  |       | created_at     |
|              |       |                  |       | updated_at     |
+--------------+       +------------------+       +----------------+
                                                           |
                                                           | 1:N
                                                           v
+------------------+       +------------------+       +----------------+
|   ChatSession    |       |    ChatMessage    |       |  TextChunk     |
|------------------|       |------------------|       |----------------|
| id (PK)          |--1:N--| id (PK)          |       | id (PK)        |
| title            |       | session_id (FK)  |       | document_id(FK)|
| user_id (FK)     |       | role (USER/AI)   |       | content        |
| kb_id (FK)       |       | content          |       | chunk_index    |
| created_at       |       | sources (JSON)   |       | token_count    |
| updated_at       |       | created_at       |       | kb_id (FK)     |
|                  |       |                  |       | created_at     |
|                  |       |                  |       |                |
|                  |       |                  |       | 向量存储在Qdrant|
|                  |       |                  |       | 不在MySQL存向量 |
+------------------+       +------------------+       +----------------+
```

### 3.2 物理表结构

#### 表1: user - 用户表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK, AUTO_INCREMENT | 主键 |
| username | varchar(50) | UNIQUE, NOT NULL | 用户名 |
| password | varchar(255) | NOT NULL | BCrypt加密密码 |
| email | varchar(100) | UNIQUE | 邮箱 |
| nickname | varchar(50) | | 昵称 |
| avatar | varchar(500) | | 头像URL |
| role | varchar(20) | DEFAULT 'USER' | 角色: USER/ADMIN |
| status | tinyint | DEFAULT 1 | 状态: 1正常 0禁用 |
| created_at | datetime | NOT NULL | 创建时间 |
| updated_at | datetime | NOT NULL | 更新时间 |

#### 表2: knowledge_base - 知识库表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK, AUTO_INCREMENT | 主键 |
| name | varchar(100) | NOT NULL | 知识库名称 |
| description | varchar(500) | | 知识库描述 |
| user_id | bigint | FK -> user.id | 创建者ID |
| status | tinyint | DEFAULT 0 | 0:空 1:处理中 2:就绪 |
| document_count | int | DEFAULT 0 | 文档数量 |
| chunk_count | int | DEFAULT 0 | 切片总数 |
| created_at | datetime | NOT NULL | 创建时间 |
| updated_at | datetime | NO
