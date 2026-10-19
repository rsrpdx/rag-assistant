# AI知识库问答平台 — 数据库设计文档

> MySQL 8, InnoDB引擎, utf8mb4字符集

---

## 一、总体设计原则

- 所有表使用 `InnoDB` 引擎，支持事务和行级锁
- 字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`（支持emoji和中文）
- 主键统一使用 `bigint` 自增，避免分布式主键冲突
- 逻辑删除使用 `is_deleted` 字段，不物理删除数据
- 统一审计字段 `created_at`、`updated_at`，由 MyBatis Plus 自动填充
- 外键不建物理约束，通过业务层保证引用完整性（便于分库分表演进）
- 所有时间字段使用 `datetime` 类型，精确到秒

---

## 二、数据库表设计

### 1. 用户表（user）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 用户ID |
| username | varchar(50) | UNIQUE, NOT NULL | | 登录用户名 |
| password | varchar(255) | NOT NULL | | BCrypt加密后的密码 |
| email | varchar(100) | UNIQUE | | 邮箱，用于找回密码 |
| nickname | varchar(50) | | | 用户昵称，为空则显示username |
| avatar | varchar(500) | | | 头像URL |
| phone | varchar(20) | UNIQUE | | 手机号 |
| role | varchar(20) | NOT NULL | 'USER' | 角色：USER(普通用户)、ADMIN(管理员) |
| status | tinyint | NOT NULL | 1 | 状态：1正常、0禁用 |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除：0未删、1已删 |
| last_login_ip | varchar(50) | | | 最后登录IP |
| last_login_time | datetime | | | 最后登录时间 |
| created_at | datetime | NOT NULL | | 创建时间 |
| updated_at | datetime | NOT NULL | | 更新时间 |

**设计说明：**
- password 字段存储 BCrypt 加密后的密文，长度255预留未来加密算法升级
- 用户名和邮箱分别唯一，保障登录唯一性
- role 字段使用字符串而非枚举，方便扩展新角色时无需DDL变更
- last_login_ip/last_login_time 记录安全审计信息

### 2. 知识库表（knowledge_base）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 知识库ID |
| name | varchar(100) | NOT NULL | | 知识库名称 |
| description | varchar(500) | | | 知识库描述 |
| user_id | bigint | NOT NULL | | 创建者ID，关联user.id |
| cover_url | varchar(500) | | | 知识库封面图 |
| status | tinyint | NOT NULL | 0 | 状态：0未就绪、1处理中、2就绪 |
| document_count | int | NOT NULL | 0 | 文档总数（冗余字段） |
| chunk_count | int | NOT NULL | 0 | 切片总数（冗余字段） |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除 |
| created_at | datetime | NOT NULL | | 创建时间 |
| updated_at | datetime | NOT NULL | | 更新时间 |

**设计说明：**
- document_count 和 chunk_count 为冗余统计字段，避免每次查询都要COUNT聚合
- status 字段标识知识库整体就绪状态，前端据此展示是否可提问
- 与user为一对多关系，一个用户可以创建多个知识库

### 3. 文件表（document）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 文件ID |
| kb_id | bigint | NOT NULL | | 所属知识库ID，关联knowledge_base.id |
| user_id | bigint | NOT NULL | | 上传者ID，关联user.id |
| filename | varchar(255) | NOT NULL | | MinIO存储文件名称（UUID重命名） |
| original_name | varchar(255) | NOT NULL | | 用户上传时的原始文件名 |
| file_size | bigint | NOT NULL | 0 | 文件大小，单位字节 |
| file_type | varchar(20) | NOT NULL | 'pdf' | 文件类型：pdf、docx、txt |
| file_url | varchar(500) | | | MinIO文件访问路径 |
| status | tinyint | NOT NULL | 0 | 文档状态：0待解析、1解析中、2已就绪、3解析失败 |
| chunk_count | int | NOT NULL | 0 | 切片数量 |
| error_msg | varchar(500) | | | 解析失败时的错误信息 |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除 |
| created_at | datetime | NOT NULL | | 创建时间 |
| updated_at | datetime | NOT NULL | | 更新时间 |

**设计说明：**
- filename 使用 UUID 重命名存储，防止同名文件覆盖和中文路径问题
- original_name 保留原始文件名，前端展示使用
- file_type 预留扩展 docx/txt 等格式
- status 字段驱动文档解析状态机流转：0 -> 1 -> 2（正常）或 3（失败）
- chunk_count 为冗余字段，直接从 text_chunk 表 COUNT 并更新

### 4. 文本切片表（text_chunk）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 切片ID |
| doc_id | bigint | NOT NULL | | 所属文档ID，关联document.id |
| kb_id | bigint | NOT NULL | | 所属知识库ID，关联knowledge_base.id |
| content | text | NOT NULL | | 切片文本内容 |
| chunk_index | int | NOT NULL | | 切片在文档中的序号（从0开始） |
| token_count | int | NOT NULL | 0 | 该切片的Token数量，用于LLM上下文预算 |
| qdrant_point_id | varchar(100) | | | Qdrant向量数据库中对应的Point ID |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除 |
| created_at | datetime | NOT NULL | | 创建时间 |

**设计说明：**
- content 使用 text 类型，支持最大65535字节，满足大多数切片大小（通常512-1024 tokens）
- chunk_index 记录切片顺序，用于按序拼接展示原文
- qdrant_point_id 建立 MySQL 与 Qdrant 的映射关系，删除文档时可同步删除向量
- kb_id 冗余字段，便于按知识库维度批量查询或删除切片
- 向量数据本身存储在 Qdrant，MySQL 仅存储元数据映射

### 5. 聊天会话表（chat_session）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 会话ID |
| title | varchar(200) | | | 会话标题，自动取用户首条消息前30字 |
| user_id | bigint | NOT NULL | | 用户ID，关联user.id |
| kb_id | bigint | NOT NULL | | 关联知识库ID，关联knowledge_base.id |
| message_count | int | NOT NULL | 0 | 消息总数（冗余字段） |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除 |
| created_at | datetime | NOT NULL | | 创建时间 |
| updated_at | datetime | NOT NULL | | 更新时间 |

**设计说明：**
- 一个会话关联一个知识库，用户切换知识库时自动创建新会话
- title 由后端在首条消息时自动截取生成，无需用户手动输入
- message_count 为冗余字段，用于会话列表展示消息条数

### 6. 聊天消息表（chat_message）

| 字段名 | 类型 | 约束 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | bigint | PK, AUTO_INCREMENT | | 消息ID |
| session_id | bigint | NOT NULL | | 所属会话ID，关联chat_session.id |
| role | varchar(10) | NOT NULL | | 消息角色：USER(用户)、AI(模型回答) |
| content | text | NOT NULL | | 消息内容（Markdown格式） |
| sources | json | | | 引用来源JSON数组，详见下方说明 |
| tokens_used | int | NOT NULL | 0 | 消耗的Token数（仅AI角色有值） |
| is_deleted | tinyint | NOT NULL | 0 | 逻辑删除 |
| created_at | datetime | NOT NULL | | 创建时间 |

**sources JSON结构示例：**
```json
[
  {
    "docId": 1,
    "docName": "Java面试指南.pdf",
    "chunkId": 10,
    "content": "原文切片片段...",
    "score": 0.95
  }
]
```

**设计说明：**
- role 使用字符串而非布尔值，更清晰可读，也方便未来扩展其他角色（如SYSTEM）
- content 采用 text 类型 + Markdown 格式，支持富文本渲染
- sources 使用 JSON 原生类型，MySQL 8 支持 JSON 索引和字段提取
- tokens_used 用于统计和费用估算，仅 AI 角色消息有效
- 消息按 created_at 升序排列即为完整的对话上下文

---

## 三、索引设计总表

| 表名 | 索引名 | 字段 | 类型 | 说明 |
|------|--------|------|------|------|
| user | idx_user_username | username | UNIQUE | 登录查询 |
| user | idx_user_email | email | UNIQUE | 邮箱唯一约束 |
| user | idx_user_phone | phone | UNIQUE | 手机号唯一约束 |
| user | idx_user_status | status | INDEX | 按状态筛选 |
| knowledge_base | idx_kb_user_id | user_id | INDEX | 查询用户的知识库列表 |
| knowledge_base | idx_kb_status | status | INDEX | 按状态筛选 |
| document | idx_doc_kb_id | kb_id | INDEX | 按知识库查文档列表 |
| document | idx_doc_user_id | user_id | INDEX | 按上传者查文档 |
| document | idx_doc_status | status | INDEX | 按解析状态筛选 |
| document | idx_doc_kb_status | kb_id, status | 复合索引 | 知识库+状态联合筛选 |
| text_chunk | idx_chunk_doc_id | doc_id | INDEX | 按文档查找所有切片 |
| text_chunk | idx_chunk_kb_id | kb_id | INDEX | 按知识库批量操作切片 |
| text_chunk | idx_chunk_doc_index | doc_id, chunk_index | UNIQUE | 文档内切片序号唯一 |
| chat_session | idx_session_user_id | user_id | INDEX | 查询用户的所有会话 |
| chat_session | idx_session_kb_id | kb_id | INDEX | 按知识库查会话 |
| chat_session | idx_session_user_kb | user_id, kb_id | 复合索引 | 用户在知识库下的会话列表 |
| chat_message | idx_msg_session_id | session_id | INDEX | 查某会话的所有消息 |
| chat_message | idx_msg_session_created | session_id, created_at | 复合索引 | 按时间排序取消息 |

---

## 四、DDL建表语句

```sql
-- =====================================================
-- 数据库创建
-- =====================================================
CREATE DATABASE IF NOT EXISTS rag_assistant
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE rag_assistant;

-- =====================================================
-- 表1: 用户表 (user)
-- =====================================================
CREATE TABLE `user` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`        VARCHAR(50)   NOT NULL                 COMMENT '登录用户名',
    `password`        VARCHAR(255)  NOT NULL                 COMMENT 'BCrypt加密密码',
    `email`           VARCHAR(100)  DEFAULT NULL             COMMENT '邮箱',
    `nickname`        VARCHAR(50)   DEFAULT NULL             COMMENT '用户昵称',
    `avatar`          VARCHAR(500)  DEFAULT NULL             COMMENT '头像URL',
    `phone`           VARCHAR(20)   DEFAULT NULL             COMMENT '手机号',
    `role`            VARCHAR(20)   NOT NULL DEFAULT 'USER'  COMMENT '角色: USER/ADMIN',
    `status`          TINYINT       NOT NULL DEFAULT 1       COMMENT '状态: 1正常 0禁用',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0未删 1已删',
    `last_login_ip`   VARCHAR(50)   DEFAULT NULL             COMMENT '最后登录IP',
    `last_login_time` DATETIME      DEFAULT NULL             COMMENT '最后登录时间',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    `updated_at`      DATETIME      NOT 
