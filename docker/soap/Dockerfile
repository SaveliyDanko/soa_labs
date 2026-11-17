FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl isu-service -am verify

FROM maven:3.9-eclipse-temurin-11 AS server
ARG TOMCAT_VERSION=9.0.124
WORKDIR /tmp
RUN curl -fsSL --retry 3 "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" -O \
    && curl -fsSL --retry 3 "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz.sha512" -O \
    && sha512sum -c "apache-tomcat-${TOMCAT_VERSION}.tar.gz.sha512" \
    && mkdir /opt/tomcat && tar -xzf "apache-tomcat-${TOMCAT_VERSION}.tar.gz" --strip-components=1 -C /opt/tomcat \
    && rm -rf /opt/tomcat/webapps/*

FROM eclipse-temurin:11-jre-jammy
ENV CATALINA_HOME=/opt/tomcat
COPY --from=server /opt/tomcat /opt/tomcat
COPY --from=build /source/isu-service/target/isu-service.war /opt/tomcat/webapps/ROOT.war
ENV JAVA_OPTS="-XX:ActiveProcessorCount=2 -Xms64m -Xmx384m"
EXPOSE 8080
CMD ["/opt/tomcat/bin/catalina.sh", "run"]
