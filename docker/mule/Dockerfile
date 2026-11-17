FROM maven:3.9-eclipse-temurin-17 AS application
WORKDIR /source
COPY mule-esb/ .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Daether.connector.requestTimeout=30000 package

FROM eclipse-temurin:17-jdk-jammy
ARG MULE_VERSION=4.9.0
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fSL --retry 3 "https://repository.mulesoft.org/nexus/service/local/repositories/releases/content/org/mule/distributions/mule-standalone/${MULE_VERSION}/mule-standalone-${MULE_VERSION}.tar.gz" -o /tmp/mule.tar.gz \
    && tar -xzf /tmp/mule.tar.gz -C /opt && mv "/opt/mule-standalone-${MULE_VERSION}" /opt/mule \
    && rm /tmp/mule.tar.gz && rm -rf /var/lib/apt/lists/*
ENV MULE_HOME=/opt/mule
RUN sed -i 's/^wrapper.java.initmemory=.*/wrapper.java.initmemory=128/; s/^wrapper.java.maxmemory=.*/wrapper.java.maxmemory=512/' /opt/mule/conf/wrapper.conf
COPY --from=application /source/target/lab4-integration-4.0.0-mule-application.jar /opt/mule/apps/lab4-integration.jar
WORKDIR /opt/mule
EXPOSE 8081
CMD ["/opt/mule/bin/mule", "console", "-M-Dmule.verbose.exceptions=false"]
