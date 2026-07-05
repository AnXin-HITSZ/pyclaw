FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 spring
COPY --from=build /workspace/target/spring-backend-0.1.0.jar /app/spring-backend.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/spring-backend.jar"]
