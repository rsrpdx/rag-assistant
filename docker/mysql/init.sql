-- =====================================================
-- RAG Assistant - Docker 初始化脚本
-- =====================================================

CREATE DATABASE IF NOT EXISTS rag_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE rag_assistant;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
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
    `updated_at`      DATETIME      NOT NULL                 COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '知识库ID',
    `name`            VARCHAR(100)  NOT NULL                 COMMENT '知识库名称',
    `description`     VARCHAR(500)  DEFAULT NULL             COMMENT '知识库描述',
    `category`        VARCHAR(50)   DEFAULT NULL             COMMENT '知识库分类',
    `user_id`         BIGINT        NOT NULL                 COMMENT '创建者ID',
    `cover_url`       VARCHAR(500)  DEFAULT NULL             COMMENT '知识库封面图',
    `status`          TINYINT       NOT NULL DEFAULT 0       COMMENT '状态: 0空 1处理中 2就绪',
    `document_count`  INT           NOT NULL DEFAULT 0       COMMENT '文档总数',
    `chunk_count`     INT           NOT NULL DEFAULT 0       COMMENT '切片总数',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL                 COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_user_id` (`user_id`),
    KEY `idx_kb_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 文件表
CREATE TABLE IF NOT EXISTS `document` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '文件ID',
    `kb_id`           BIGINT        NOT NULL                 COMMENT '所属知识库ID',
    `user_id`         BIGINT        NOT NULL                 COMMENT '上传者ID',
    `filename`        VARCHAR(255)  NOT NULL                 COMMENT 'MinIO存储文件名',
    `original_name`   VARCHAR(255)  NOT NULL                 COMMENT '原始文件名',
    `file_size`       BIGINT        NOT NULL DEFAULT 0       COMMENT '文件大小(字节)',
    `file_type`       VARCHAR(20)   NOT NULL DEFAULT 'pdf'   COMMENT '文件类型',
    `file_url`        VARCHAR(500)  DEFAULT NULL             COMMENT 'MinIO访问路径',
    `status`          TINYINT       NOT NULL DEFAULT 0       COMMENT '状态: 0待解析 1解析中 2已就绪 3失败',
    `chunk_count`     INT           NOT NULL DEFAULT 0       COMMENT '切片数量',
    `error_msg`       VARCHAR(500)  DEFAULT NULL             COMMENT '解析失败信息',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL                 COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_doc_kb_id` (`kb_id`),
    KEY `idx_doc_user_id` (`user_id`),
    KEY `idx_doc_status` (`status`),
    KEY `idx_doc_kb_status` (`kb_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';

-- 文本切片表
CREATE TABLE IF NOT EXISTS `text_chunk` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '切片ID',
    `doc_id`          BIGINT        NOT NULL                 COMMENT '所属文档ID',
    `kb_id`           BIGINT        NOT NULL                 COMMENT '所属知识库ID',
    `content`         TEXT          NOT NULL                 COMMENT '切片文本内容',
    `chunk_index`     INT           NOT NULL                 COMMENT '切片序号',
    `token_count`     INT           NOT NULL DEFAULT 0       COMMENT 'Token数量',
    `qdrant_point_id` VARCHAR(100)  DEFAULT NULL             COMMENT 'Qdrant向量Point ID',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chunk_doc_id` (`doc_id`),
    KEY `idx_chunk_kb_id` (`kb_id`),
    UNIQUE KEY `uk_doc_chunk_index` (`doc_id`, `chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文本切片表';

-- 聊天会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '会话ID',
    `title`           VARCHAR(200)  DEFAULT NULL             COMMENT '会话标题',
    `user_id`         BIGINT        NOT NULL                 COMMENT '用户ID',
    `kb_id`           BIGINT        NOT NULL                 COMMENT '关联知识库ID',
    `message_count`   INT           NOT NULL DEFAULT 0       COMMENT '消息总数',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL                 COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_user_id` (`user_id`),
    KEY `idx_session_kb_id` (`kb_id`),
    KEY `idx_session_user_kb` (`user_id`, `kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    `session_id`      BIGINT        NOT NULL                 COMMENT '所属会话ID',
    `role`            VARCHAR(10)   NOT NULL                 COMMENT '角色: USER AI',
    `content`         TEXT          NOT NULL                 COMMENT '消息内容(Markdown)',
    `sources`         JSON         DEFAULT NULL              COMMENT '引用来源JSON',
    `tokens_used`     INT           NOT NULL DEFAULT 0       COMMENT '消耗Token数',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    `created_at`      DATETIME      NOT NULL                 COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_msg_session_id` (`session_id`),
    KEY `idx_msg_session_created` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
