FROM payara/server-full:6.2025.11-jdk17

COPY --chown=payara:payara docker/tls/server.p12 /opt/payara/appserver/glassfish/domains/domain1/config/keystore.p12
COPY --chown=payara:payara docker/tls/server.crt /tmp/soa-lab-server.crt
RUN keytool -importcert -noprompt -alias soa-lab-server \
    -file /tmp/soa-lab-server.crt \
    -keystore /opt/payara/appserver/glassfish/domains/domain1/config/cacerts.p12 \
    -storetype PKCS12 -storepass changeit \
    && rm /tmp/soa-lab-server.crt
COPY --chown=payara:payara docker/payara/pre-boot-commands.asadmin /opt/payara/config/pre-boot-commands.asadmin
COPY --chown=payara:payara study-groups-service/target/study-groups.war /opt/payara/deployments/ROOT.war

EXPOSE 8181
