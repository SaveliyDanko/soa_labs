FROM quay.io/wildfly/wildfly:39.0.1.Final-2-jdk17

COPY --chown=jboss:root docker/tls/server.p12 docker/tls/truststore.p12 /opt/jboss/wildfly/standalone/configuration/
COPY --chown=jboss:root docker/wildfly/configure-https.cli /tmp/configure-https.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configure-https.cli \
    && rm /tmp/configure-https.cli \
    && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history
COPY --chown=jboss:root isu-service/target/isu.war /opt/jboss/wildfly/standalone/deployments/ROOT.war

EXPOSE 8443
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
