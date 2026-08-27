FROM cassandra:5.0

ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar \
    /opt/jmx_prometheus_javaagent.jar
RUN chmod 644 /opt/jmx_prometheus_javaagent.jar

COPY cassandra.yml /etc/jmx-exporter/cassandra.yml
