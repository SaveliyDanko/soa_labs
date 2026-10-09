FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Daether.connector.requestTimeout=30000 verify

FROM eclipse-temurin:11-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG MODULE
WORKDIR /app
COPY --from=build /source/${MODULE}/target/${MODULE}.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2 -Xms64m -Xmx256m"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
