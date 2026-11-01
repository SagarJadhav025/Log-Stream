# Use Java 21 (matching your project)
FROM eclipse-temurin:21-jdk-alpine

# Copy the built jar file into the Docker image
COPY target/*.jar app.jar

# Expose the gRPC port
EXPOSE 9090

# Command to run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]