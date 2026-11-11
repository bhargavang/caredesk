# Build with the Maven wrapper, run on a slim JRE as a non-root user.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/caredesk-backend-*.jar app.jar

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home caredesk
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER caredesk
EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
