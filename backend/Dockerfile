# Multi-stage build: compile with the Maven wrapper, run on a slim JRE as a non-root user.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 bookeasy && mkdir -p /var/data/uploads && chown -R bookeasy /var/data
WORKDIR /app
COPY --from=build /app/target/bookeasy-backend-*.jar app.jar
USER bookeasy
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
