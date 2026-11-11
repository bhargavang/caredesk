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
# Unpack the jar and record which classes startup loads (class data sharing). Starting from that
# archive is about a third faster, which matters on small instances that sleep when idle.
# The training run uses the in-memory database and stops as soon as the app has started.
RUN java -Djarmode=tools -jar app.jar extract --destination extracted && rm app.jar \
    && java -XX:ArchiveClassesAtExit=extracted/app.jsa -Dspring.context.exit=onRefresh \
       -Dcaredesk.seed.enabled=false -Dcaredesk.jobs.enabled=false -Dserver.port=0 \
       -Dlogging.level.root=warn -Xlog:cds=off -Xlog:cds+dynamic=off -jar extracted/app.jar
USER caredesk
EXPOSE 8080
# Size the heap from the container's memory limit, leaving room for metaspace and threads.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65"
ENTRYPOINT ["java", "-XX:SharedArchiveFile=extracted/app.jsa", "-jar", "extracted/app.jar"]
