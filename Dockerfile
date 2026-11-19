# ---- Stage 1: build the jar with Maven and JDK 21 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the pom first so dependencies are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Stage 2: small runtime image with only a JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /app/target/report-generator-*.jar app.jar
USER appuser

# Hosts like Render and Railway pass the port in the PORT variable (the app reads it).
ENV PORT=8080
# Keep memory use inside small free-tier containers.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
