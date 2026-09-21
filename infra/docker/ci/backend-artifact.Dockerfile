FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

WORKDIR /app/certmuse
RUN mkdir -p /app/certmuse/logs /app/certmuse/tmp

COPY backend/ruoyi-admin/target/ruoyi-admin.jar /app/certmuse/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dserver.port=8080 -jar /app/certmuse/app.jar"]
