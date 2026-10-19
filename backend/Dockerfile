# ===================== Build Stage =====================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ===================== Run Stage =====================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
