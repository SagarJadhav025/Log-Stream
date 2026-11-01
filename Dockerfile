# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src
# Build the application (skipping tests to speed up deployment)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# Copy the built jar file from the 'build' stage
COPY --from=build /app/target/*.jar app.jar
# Expose the gRPC port
EXPOSE 9090
# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]