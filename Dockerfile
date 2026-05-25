FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/memes-bot-1.0.0.jar /app/memes-bot.jar

RUN mkdir -p /app/data

ENV SQLITE_JDBC_URL=jdbc:sqlite:/app/data/memesbot.db

VOLUME ["/app/data"]

CMD ["java", "-jar", "/app/memes-bot.jar"]
