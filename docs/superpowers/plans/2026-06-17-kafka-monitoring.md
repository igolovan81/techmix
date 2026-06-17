# Kafka Prometheus & Grafana Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Prometheus and Grafana monitoring to the Kafka demo's 3-broker cluster, mirroring the existing RabbitMQ monitoring setup's structure.

**Architecture:** A `kafka-exporter` sidecar container polls the existing Kafka brokers over the normal Kafka protocol and exposes Prometheus-format metrics; a `prometheus` container scrapes it; a `grafana` container visualizes it via an auto-provisioned datasource and a pre-loaded dashboard. No changes to the existing `kafka1`/`kafka2`/`kafka3` service definitions. This is pure infrastructure — no Java code, no unit tests; verification is done by bringing the stack up and checking real endpoints/UI.

**Tech Stack:** Docker Compose, `danielqsj/kafka-exporter:latest`, `prom/prometheus:v2.53.0`, `grafana/grafana:11.1.0`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-17-kafka-monitoring-design.md` — follow it exactly.
- Use the `kafka-exporter` sidecar approach (Kafka-protocol based) — NOT a JMX Java agent. Do not modify the `kafka1`/`kafka2`/`kafka3` service definitions in `message-brokers/kafka/docker/docker-compose.yml`.
- `kafka-exporter` connects via the existing `INTERNAL` listener: `kafka1:29092,kafka2:29092,kafka3:29092` (the same addresses `kafka-ui` already uses).
- Host ports: Prometheus `9091`, Grafana `3001` — deliberately different from RabbitMQ's `9090`/`3000` so both demos' monitoring stacks can run side by side without colliding.
- Grafana credentials: `admin`/`admin` (matches RabbitMQ's setup), `GF_USERS_ALLOW_SIGN_UP: "false"`.
- Prometheus version `v2.53.0`, Grafana version `11.1.0` — exact versions, matching RabbitMQ's stack.
- Dashboard has exactly 6 panels: messages in/sec per topic, consumer group lag, under-replicated partitions, broker count, partition count per topic, topic count.
- Do not modify `message-brokers/rabbitmq/README.md` — out of scope for this plan.

---

### Task 1: Add the metrics pipeline (kafka-exporter, Prometheus, datasource)

**Files:**
- Modify: `message-brokers/kafka/docker/docker-compose.yml`
- Create: `message-brokers/kafka/docker/prometheus/prometheus.yml`
- Create: `message-brokers/kafka/docker/grafana/provisioning/datasources/prometheus.yml`

**Interfaces:**
- Produces: a running `kafka-exporter` container exposing `:9308/metrics` on the `kafka_network`, scraped by a running `prometheus` container at `http://localhost:9091`. Task 2 (Grafana dashboard) and Task 3 (README) depend on this being up and the scrape target healthy.

- [ ] **Step 1: Add the `kafka-exporter` service**

Open `message-brokers/kafka/docker/docker-compose.yml`. Find the `kafka-ui` service block (it ends right before the `networks:` top-level key). Insert a new `kafka-exporter` service immediately after `kafka-ui`'s block (still inside `services:`):

```yaml
  kafka-exporter:
    image: danielqsj/kafka-exporter:latest
    container_name: kafka-exporter
    command:
      - --kafka.server=kafka1:29092
      - --kafka.server=kafka2:29092
      - --kafka.server=kafka3:29092
    depends_on:
      kafka1:
        condition: service_healthy
    networks:
      - kafka_network
```

- [ ] **Step 2: Add the `prometheus` service**

Immediately after the `kafka-exporter` block you just added, insert:

```yaml
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: kafka-prometheus
    ports:
      - "9091:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=7d
    depends_on:
      - kafka-exporter
    networks:
      - kafka_network
```

- [ ] **Step 3: Create the Prometheus scrape config**

Create `message-brokers/kafka/docker/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: kafka
    static_configs:
      - targets:
          - kafka-exporter:9308
    metrics_path: /metrics
```

- [ ] **Step 4: Create the Grafana datasource provisioning file**

Create `message-brokers/kafka/docker/grafana/provisioning/datasources/prometheus.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    url: http://prometheus:9090
    isDefault: true
    access: proxy
    jsonData:
      httpMethod: POST
      timeInterval: 15s
```

- [ ] **Step 5: Bring the cluster up and verify the scrape target**

Run:
```bash
cd message-brokers/kafka/docker
docker compose up -d
```
Wait ~30 seconds, then check:
```bash
docker compose ps
```
Expected: `kafka1`, `kafka2`, `kafka3` healthy; `kafka-ui`, `kafka-exporter`, `prometheus` running.

Run:
```bash
curl -s http://localhost:9091/api/v1/targets | grep -o '"health":"[a-z]*"'
```
Expected: at least one `"health":"up"` (the `kafka` job's target). If it shows `"down"`, run `docker logs kafka-exporter` and check it connected to the brokers — confirm the `--kafka.server` addresses match `kafka1:29092` etc. exactly as configured in Step 1.

- [ ] **Step 6: Commit**

```bash
git add message-brokers/kafka/docker/docker-compose.yml message-brokers/kafka/docker/prometheus/prometheus.yml message-brokers/kafka/docker/grafana/provisioning/datasources/prometheus.yml
git commit -m "$(cat <<'EOF'
feat(kafka): add kafka-exporter and Prometheus metrics pipeline

kafka-exporter polls the existing 3-broker cluster over the Kafka
protocol (same INTERNAL listener kafka-ui already uses) and exposes
Prometheus-format metrics; Prometheus scrapes it every 15s. No changes
to the kafka1/kafka2/kafka3 service definitions. Host port 9091 (not
9090) so this can run alongside RabbitMQ's monitoring stack.
EOF
)"
```

---

### Task 2: Add the Grafana dashboard

**Files:**
- Modify: `message-brokers/kafka/docker/docker-compose.yml`
- Create: `message-brokers/kafka/docker/grafana/provisioning/dashboards/provider.yml`
- Create: `message-brokers/kafka/docker/grafana/dashboards/kafka.json`

**Interfaces:**
- Consumes: the running `prometheus`/`kafka-exporter` pipeline from Task 1 (datasource provisioning file already in place).
- Produces: a Grafana instance at `http://localhost:3001` with a pre-loaded "Kafka Demo Cluster" dashboard. Task 4's verification depends on this.

- [ ] **Step 1: Add the `grafana` service**

In `message-brokers/kafka/docker/docker-compose.yml`, immediately after the `prometheus` block (added in Task 1), insert:

```yaml
  grafana:
    image: grafana/grafana:11.1.0
    container_name: kafka-grafana
    ports:
      - "3001:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
    depends_on:
      - prometheus
    networks:
      - kafka_network
```

- [ ] **Step 2: Create the dashboard provider config**

Create `message-brokers/kafka/docker/grafana/provisioning/dashboards/provider.yml`:

```yaml
apiVersion: 1

providers:
  - name: Kafka
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] **Step 3: Create the dashboard JSON**

Create `message-brokers/kafka/docker/grafana/dashboards/kafka.json`:

```json
{
  "annotations": {
    "list": []
  },
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 0,
  "id": null,
  "links": [],
  "panels": [
    {
      "id": 1,
      "title": "Messages In/sec per Topic",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum by (topic) (rate(kafka_topic_partition_current_offset[1m]))",
          "legendFormat": "{{topic}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "title": "Consumer Group Lag",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum by (consumergroup, topic) (kafka_consumergroup_lag)",
          "legendFormat": "{{consumergroup}} / {{topic}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "title": "Under-Replicated Partitions",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 8 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(kafka_topic_partition_under_replicated_partition)",
          "legendFormat": "Under-replicated",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "title": "Broker Count",
      "type": "stat",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 8 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "kafka_brokers",
          "legendFormat": "Brokers",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "title": "Partition Count per Topic",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 16 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "kafka_topic_partitions",
          "legendFormat": "{{topic}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 6,
      "title": "Topic Count",
      "type": "stat",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 16 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "count(count by (topic) (kafka_topic_partitions))",
          "legendFormat": "Topics",
          "refId": "A"
        }
      ]
    }
  ],
  "refresh": "10s",
  "schemaVersion": 39,
  "tags": ["kafka"],
  "templating": { "list": [] },
  "time": { "from": "now-15m", "to": "now" },
  "timepicker": {},
  "timezone": "browser",
  "title": "Kafka Demo Cluster",
  "uid": "kafka-demo-cluster",
  "version": 1
}
```

- [ ] **Step 4: Validate the JSON and bring Grafana up**

Run: `python3 -m json.tool message-brokers/kafka/docker/grafana/dashboards/kafka.json > /dev/null && echo "valid JSON"`
Expected: `valid JSON`

Run:
```bash
cd message-brokers/kafka/docker
docker compose up -d
```
Wait ~15 seconds, then check:
```bash
docker compose ps
```
Expected: `grafana` running (in addition to everything from Task 1).

- [ ] **Step 5: Confirm the dashboard loaded**

Run:
```bash
curl -s -u admin:admin http://localhost:3001/api/search?query=Kafka
```
Expected: JSON array containing one object with `"title":"Kafka Demo Cluster"`. If the array is empty, run `docker logs kafka-grafana | grep -i dashboard` and check for a provisioning error (e.g. invalid JSON, wrong path) — re-check Step 3's file content if so.

- [ ] **Step 6: Commit**

```bash
git add message-brokers/kafka/docker/docker-compose.yml message-brokers/kafka/docker/grafana/provisioning/dashboards/provider.yml message-brokers/kafka/docker/grafana/dashboards/kafka.json
git commit -m "$(cat <<'EOF'
feat(kafka): add Grafana with pre-loaded Kafka Demo Cluster dashboard

Six panels: messages in/sec per topic, consumer group lag,
under-replicated partitions, broker count, partition count per topic,
and topic count. Host port 3001 (not 3000) so this can run alongside
RabbitMQ's Grafana instance.
EOF
)"
```

---

### Task 3: Document the monitoring stack in the Kafka README

**Files:**
- Modify: `message-brokers/kafka/README.md`

**Interfaces:**
- Consumes: the URLs and ports fixed in Tasks 1-2 (`http://localhost:9091`, `http://localhost:3001`, admin/admin).
- Produces: nothing consumed by later tasks — documentation only.

- [ ] **Step 1: Add the URLs to "Start the cluster"**

In `message-brokers/kafka/README.md`, find this line (currently on its own, right after the cluster-verification command block):

```markdown
Kafka UI: http://localhost:8090
```

Replace it with:

```markdown
Kafka UI: http://localhost:8090

Grafana: http://localhost:3001 (admin/admin)
Prometheus: http://localhost:9091
```

- [ ] **Step 2: Add a "Monitoring" section before "Stop the cluster"**

Find this exact block near the end of the file:

```markdown
### Kafka UI shortcuts

| URL | Purpose |
|---|---|
| http://localhost:8090 | Cluster overview — broker count, topic count, throughput |
| http://localhost:8090/ui/clusters/local/topics | Per-topic partition list, message counts, configs |
| http://localhost:8090/ui/clusters/local/consumer-groups | Consumer group lag per partition |

## Stop the cluster
```

Replace it with:

```markdown
### Kafka UI shortcuts

| URL | Purpose |
|---|---|
| http://localhost:8090 | Cluster overview — broker count, topic count, throughput |
| http://localhost:8090/ui/clusters/local/topics | Per-topic partition list, message counts, configs |
| http://localhost:8090/ui/clusters/local/consumer-groups | Consumer group lag per partition |

## Monitoring

`kafka-exporter` polls the cluster over the Kafka protocol (the same `INTERNAL` listener Kafka UI uses) and exposes Prometheus-format metrics — no changes to the broker containers themselves. Prometheus scrapes it every 15 seconds; Grafana visualizes the result.

| URL | Purpose |
|---|---|
| http://localhost:9091 | Prometheus — query metrics directly, check scrape target health under `/targets` |
| http://localhost:3001 | Grafana (admin/admin) — pre-loaded "Kafka Demo Cluster" dashboard |

**Dashboard panels:** messages in/sec per topic, consumer group lag, under-replicated partitions (replication health), broker count, partition count per topic, topic count.

## Stop the cluster
```

- [ ] **Step 3: Commit**

```bash
git add message-brokers/kafka/README.md
git commit -m "$(cat <<'EOF'
docs(kafka): document the Prometheus/Grafana monitoring stack

Adds the Grafana/Prometheus URLs to the cluster startup section and a
new Monitoring section describing the kafka-exporter metrics pipeline
and the six dashboard panels.
EOF
)"
```

---

### Task 4: Full verification

**Files:** none (verification only — no files change unless a problem found in Tasks 1-3 needs fixing).

**Interfaces:**
- Consumes: the full stack from Tasks 1-3 — this is the spec's "Testing / verification" section executed end-to-end.

- [ ] **Step 1: Confirm the cluster and monitoring stack are up**

```bash
cd message-brokers/kafka/docker
docker compose ps
```
Expected: `kafka1`, `kafka2`, `kafka3` healthy; `kafka-ui`, `kafka-exporter`, `prometheus`, `grafana` running. If anything is missing, run `docker compose up -d` again and re-check.

- [ ] **Step 2: Confirm the Prometheus scrape target is up**

```bash
curl -s http://localhost:9091/api/v1/targets | python3 -c "import json,sys; d=json.load(sys.stdin); print([t['health'] for t in d['data']['activeTargets']])"
```
Expected: `['up']` (or a list containing `'up'`).

- [ ] **Step 3: Generate Kafka traffic**

```bash
cd ../spring-demo
mvn spring-boot:run > /tmp/kafka-app.log 2>&1 &
sleep 20
curl -X POST "http://localhost:8080/demo/simple?message=hello"
curl -X POST "http://localhost:8080/demo/work?message=task&count=5"
curl -X POST "http://localhost:8080/demo/pubsub?message=broadcast"
curl -X POST "http://localhost:8080/demo/partition?key=error&message=boom"
```
Expected: all four `curl` calls return HTTP 200.

- [ ] **Step 4: Confirm the dashboard shows real data**

```bash
curl -s -u admin:admin "http://localhost:3001/api/datasources/proxy/uid/prometheus/api/v1/query?query=kafka_brokers"
```
Expected: JSON response with `"status":"success"` and a result value of `3` (three brokers). If the value is missing or `0`, re-check Task 1 Step 5's troubleshooting (kafka-exporter's connection to the brokers).

```bash
curl -s -u admin:admin "http://localhost:3001/api/datasources/proxy/uid/prometheus/api/v1/query?query=sum(rate(kafka_topic_partition_current_offset[1m]))"
```
Expected: `"status":"success"` with a non-error result (value may be `0` immediately after the burst of test messages settles, but the query itself must succeed).

- [ ] **Step 5: Stop the app and tear down**

```bash
# Stop the spring-boot:run background process
pkill -f "KafkaDemoApplication" 2>/dev/null; pkill -f "spring-boot:run" 2>/dev/null
cd ../docker
docker compose down
```

If any step in this task surfaced a problem, go back and fix the relevant file in Task 1-3, re-commit, and re-run verification from Step 1.
