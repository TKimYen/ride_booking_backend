# syntax=docker/dockerfile:1
#
# Dockerfile dùng chung cho cả 6 service, chọn module bằng build arg MODULE.
#   docker build --build-arg MODULE=auth-service -t ride-booking/auth-service .
#
# Giai đoạn build dùng Maven để biên dịch, giai đoạn chạy chỉ copy JRE + jar
# nên image cuối nhỏ hơn nhiều so với dùng chung image Maven.
# Dùng bản alpine vì đã có sẵn wget (busybox) cho HEALTHCHECK trong compose.

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

# Copy pom trước để tận dụng cache Docker: đổi source không làm tải lại dependency.
COPY pom.xml .
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY booking-service/pom.xml booking-service/
COPY payment-service/pom.xml payment-service/

RUN mvn -B -q dependency:go-offline -DexcludeArtifactIds=ride-booking-microservices || true

ARG MODULE
COPY discovery-server/src discovery-server/src
COPY api-gateway/src api-gateway/src
COPY auth-service/src auth-service/src
COPY user-service/src user-service/src
COPY booking-service/src booking-service/src
COPY payment-service/src payment-service/src

RUN mvn -B clean package -DskipTests -pl ${MODULE} -am \
    && cp ${MODULE}/target/*.jar /workspace/app.jar

FROM ${RUNTIME_IMAGE} AS runtime

WORKDIR /app

# Không chạy bằng root
RUN adduser -D -u 10001 appuser

COPY --from=build /workspace/app.jar app.jar

USER appuser

ARG MODULE
ENV MODULE=${MODULE}

# 8761 discovery, 8088 gateway, 8081 auth, 8082 user, 8083 booking, 8084 payment
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8088 8081 8082 8083 8084 8761

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]