# ビルド段階: コンテナ内で jar を作成（手元の target/ 不要）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && mv target/pontal-*.jar /build/app.jar

# 実行段階: JRE だけで起動
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
