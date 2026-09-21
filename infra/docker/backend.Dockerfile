# syntax=docker/dockerfile:1.7
# Production image: compile the application in the image build so Compose does
# not depend on a JAR left behind by a developer's local Maven installation.
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace/backend
COPY backend/ ./

# The cache is intentionally outside the resulting image. It speeds up repeat
# builds without making the runtime image carry Maven or its dependency cache.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x ./mvnw \
    && ./mvnw -Pprod -pl ruoyi-admin -am package -DskipTests

FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76

ARG CERTMUSE_SOURCE_REVISION
LABEL org.opencontainers.image.revision=$CERTMUSE_SOURCE_REVISION \
      org.opencontainers.image.title="CertMuse backend"

RUN addgroup -S certmuse \
    && adduser -S -G certmuse -h /app/certmuse certmuse \
    && apk add --no-cache busybox-extras su-exec \
    && mkdir -p /app/certmuse/logs /app/certmuse/tmp \
    && chown -R certmuse:certmuse /app/certmuse

WORKDIR /app/certmuse
COPY --from=build --chown=certmuse:certmuse /workspace/backend/ruoyi-admin/target/ruoyi-admin.jar ./app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

EXPOSE 8080

# Named Docker volumes are created as root.  Reconcile their ownership on every
# boot so an existing local volume cannot prevent Logback from opening its files;
# the JVM still runs as the unprivileged application user.
ENTRYPOINT ["sh", "-c", "chown -R certmuse:certmuse /app/certmuse/logs /app/certmuse/tmp && exec su-exec certmuse java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dserver.port=8080 -jar /app/certmuse/app.jar"]
