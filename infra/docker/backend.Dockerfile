FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace/backend
COPY backend/ ./
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/backend/ruoyi-admin/target/ruoyi-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=8080 -jar /app/app.jar"]
