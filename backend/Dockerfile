# Stage 1: Dependency Caching
FROM maven:3.9.9-eclipse-temurin-17 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Stage 2: Build
FROM deps AS build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]