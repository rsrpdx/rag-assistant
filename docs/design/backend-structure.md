# AI知识库问答平台 — Spring Boot 后端工程结构设计

> Java 21 + Spring Boot 3.x + MyBatis Plus + Spring Security + JWT + Redis

---

## 一、Maven 依赖清单

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.rag</groupId>
    <artifactId>rag-assistant</artifactId>
    <version>1.0.0</version>
    <name>rag-assistant</name>
    <description>AI知识库问答平台后端服务</description>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <jjwt.version>0.12.6</jjwt.version>
        <langchain4j.version>1.0.0-beta1</langchain4j.version>
        <minio.version>8.5.14</minio.version>
        <pdfbox.version>3.0.3</pdfbox.version>
        <hutool.version>5.8.34</hutool.version>
    </properties>

    <dependencies>
        <!-- ====== Spring Boot Starters ====== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ====== MyBatis Plus ====== -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- ====== MySQL Driver ====== -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- ====== JWT ====== -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ====== LangChain4j ====== -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-qdrant</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- ====== MinIO ====== -->
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>${minio.version}</version>
        </dependency>

        <!-- ====== PDFBox ====== -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>${pdfbox.version}</version>
        </dependency>

        <!-- ====== Tools ====== -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ====== Test ====== -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 依赖选型说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.4.1 | RESTful API + SSE流式响应(Tomcat) |
| spring-boot-starter-security | 3.4.1 | 认证授权框架 |
| spring-boot-starter-data-redis | 3.4.1 | JWT黑名单 + 限流 + 缓存 |
| mybatis-plus-spring-boot3-starter | 3.5.9 | MyBatis增强，代码生成器支持 |
| jjwt-api/impl/jackson | 0.12.6 | JWT令牌生成与校验 |
| langchain4j-open-ai | 1.0.0-beta1 | OpenAI兼容接口(LLM+Embedding) |
| langchain4j-qdrant | 1.0.0-beta1 | Qdrant向量数据库集成 |
| langchain4j-document-parser-apache-pdfbox | 1.0.0-beta1 | PDF文档解析 |
| minio | 8.5.14 | 文件对象存储 |
| hutool-all | 5.8.34 | 工具类集合(Bean复制/加密/ID生成等) |
| h2database | 测试用 | 单元测试嵌入式数据库 |

---

## 二、完整工程目录结构

```
backend/
|-- src/main/java/com/rag/assistant/
|   |-- AssistantApplication.java                       # Spring Boot启动类
|   |
|   |-- common/                                          # [公共模块]
|   |   |-- config/
|   |   |   |-- JacksonConfig.java                       # Jackson序列化配置
|   |   |   |-- MyBatisPlusConfig.java                   # MyBatis Plus分页/填充配置
|   |   |   |-- RedisConfig.java                         # Redis序列化配置
|   |   |   |-- CorsConfig.java                          # 跨域配置
|   |   |   |-- WebMvcConfig.java                        # WebMVC拦截器/资源映射
|   |   |   |-- AsyncConfig.java                         # 异步任务线程池配置
|   |   |-- constant/
|   |   |   |-- ApiConstant.java                         # API通用常量
|   |   |   |-- CacheConstant.java                       # Redis缓存Key常量
|   |   |   |-- CommonConstant.java                      # 通用常量
|   |   |-- enums/
|   |   |   |-- ResultCodeEnum.java                      # 业务返回码枚举
|   |   |   |-- DocumentStatusEnum.java                  # 文档状态枚举
|   |   |   |-- KbStatusEnum.java                        # 知识库状态枚举
|   |   |-- exception/
|   |   |   |-- GlobalExceptionHandler.java              # 全局异常处理器
|   |   |   |-- BusinessException.java                   # 业务异常
|   |   |   |-- UnauthorizedException.java               # 未授权异常
|   |   |   |-- ForbiddenException.java  
