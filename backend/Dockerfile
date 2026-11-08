# Multi-stage build: compile with the Maven wrapper, run on a slim JRE as a non-root user.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Demo photos (seed/photos) laid out as storage keys (public/demo/...), so the demo data shows its pictures.
FROM python:3.12-slim AS photos
COPY seed /seed
RUN python /seed/install_demo_photos.py /demo-uploads

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 bookeasy && mkdir -p /var/data/uploads && chown -R bookeasy /var/data
WORKDIR /app
COPY --from=build /app/target/bookeasy-backend-*.jar app.jar
COPY --from=photos --chown=bookeasy /demo-uploads /app/demo-uploads
USER bookeasy
EXPOSE 8080
# Copy any missing demo photos into file storage (a persistent disk, or the container's own disk on a free
# instance), never overwriting existing files, then start the API.
ENTRYPOINT ["sh", "-c", "STORE=\"${FILE_STORAGE_PATH:-/var/data/uploads}\"; mkdir -p \"$STORE\" && cp -rn /app/demo-uploads/. \"$STORE\"/; exec java -jar /app/app.jar"]
