# MongoDB Cluster Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Prometheus and Grafana monitoring to the MongoDB demo's 3-node replica set, mirroring the existing Kafka monitoring setup's structure.

**Architecture:** A `mongodb-exporter` sidecar container (`percona/mongodb_exporter`) connects to the existing replica set over the native MongoDB protocol and exposes Prometheus-format metrics; a `prometheus` container scrapes it; a `grafana` container visualizes it via an auto-provisioned datasource and a pre-loaded dashboard. No changes to the existing `mongo1`/`mongo2`/`mongo3` service definitions. This is pure infrastructure — no Java code, no unit tests; verification is done by bringing the stack up and checking real endpoints.

**Tech Stack:** Docker Compose, `percona/mongodb_exporter:0.44`, `prom/prometheus:v2.53.0`, `grafana/grafana:11.1.0`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-18-mongodb-observability-design.md` — follow it exactly.
- `mongodb-exporter` connects via the existing internal hostnames: `mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0` (the same ones the app and `mongo-express` already use). Do not modify the `mongo1`/`mongo2`/`mongo3` service definitions in `noSQL/mongodb/docker/docker-compose.yml`.
- Host ports: Prometheus `9095`, Grafana `3002` — chosen clear of every port already used anywhere in this repo (RabbitMQ: `9090`/`3000`, Kafka: `9091`/`3001` and broker ports `9092`-`9094`), so all three monitoring stacks can run side by side.
- Grafana credentials: `admin`/`admin`, `GF_USERS_ALLOW_SIGN_UP: "false"` — matches Kafka/RabbitMQ.
- Prometheus version `v2.53.0`, Grafana version `11.1.0` — exact versions, matching the existing stacks.
- Dashboard has exactly 6 panels: replica set member state, replication lag, op counters, active connections, WiredTiger cache/memory, document counts for `products`/`orders`.
- `percona/mongodb_exporter`'s exact metric names vary by version and are pinned at image tag `0.44` for reproducibility. **Exact PromQL in this plan is a best-effort starting point** — Task 1 includes a step to discover the real metric names from the live exporter, and Task 2 includes a verification+fallback step for any panel whose query doesn't match what Task 1 discovered.
- No new browsing/admin UI — `mongo-express` (already running from the cluster's original setup) covers that role.

---

### Task 1: Metrics pipeline (mongodb-exporter, Prometheus, datasource)

**Files:**
- Modify: `noSQL/mongodb/docker/docker-compose.yml`
- Create: `noSQL/mongodb/docker/prometheus/prometheus.yml`
- Create: `noSQL/mongodb/docker/grafana/provisioning/datasources/prometheus.yml`

**Interfaces:**
- Produces: a running `mongodb-exporter` container exposing `:9216/metrics` on `mongo_network`, scraped by a running `prometheus` container at `http://localhost:9095`, plus a written-down list of the exporter's real metric names (from Step 6) that Task 2 depends on.

- [ ] **Step 1: Add the `mongodb-exporter` service**

Open `noSQL/mongodb/docker/docker-compose.yml`. Find the `mongo-express` service block (it ends right before the `networks:` top-level key). Insert a new `mongodb-exporter` service immediately after `mongo-express`'s block (still inside `services:`):

```yaml
  mongodb-exporter:
    image: percona/mongodb_exporter:0.44
    container_name: mongodb-exporter
    command:
      - --mongodb.uri=mongodb://mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0
      - --collect-all
    depends_on:
      mongo-init:
        condition: service_completed_successfully
    networks:
      - mongo_network
```

- [ ] **Step 2: Add the `prometheus` service**

Immediately after the `mongodb-exporter` block, insert:

```yaml
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: mongodb-prometheus
    ports:
      - "9095:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=7d
    depends_on:
      - mongodb-exporter
    networks:
      - mongo_network
```

- [ ] **Step 3: Create the Prometheus scrape config**

Create `noSQL/mongodb/docker/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: mongodb
    static_configs:
      - targets:
          - mongodb-exporter:9216
    metrics_path: /metrics
```

- [ ] **Step 4: Create the Grafana datasource provisioning file**

Create `noSQL/mongodb/docker/grafana/provisioning/datasources/prometheus.yml`:

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
cd noSQL/mongodb/docker
docker compose up -d
```
Wait ~30 seconds, then check:
```bash
docker compose ps
```
Expected: `mongo1`, `mongo2`, `mongo3` healthy; `mongo-express`, `mongodb-exporter`, `prometheus` running.

Run:
```bash
curl -s http://localhost:9095/api/v1/targets | grep -o '"health":"[a-z]*"'
```
Expected: at least one `"health":"up"` (the `mongodb` job's target). If it shows `"down"`, run `docker logs mongodb-exporter` and check it connected to the replica set — confirm the `--mongodb.uri` matches Step 1 exactly.

- [ ] **Step 6: Discover the exporter's real metric names**

Run:
```bash
docker exec mongodb-exporter wget -qO- http://localhost:9216/metrics | grep -E "^mongodb_(up|rs_members|ss_opcounters|ss_connections|ss_mem|connections|op_counters|memory|collstats_count|replset)" | grep "^[a-z_]* " | cut -d' ' -f1 | sort -u
```
Expected: a list of real metric names exposed by this exact exporter version. Write these down (or keep this terminal output handy) — Task 2 needs them to write working PromQL. Common families to expect: replica-set state/optime metrics (prefixed `mongodb_rs_` or `mongodb_mongod_replset_`), op counters (`mongodb_op_counters_total` or `mongodb_ss_opcounters_*`), connections (`mongodb_connections` or `mongodb_ss_connections_*`), memory (`mongodb_memory` or `mongodb_ss_mem_*`), and collection stats (`mongodb_collstats_count` or similar, present because `--collect-all` is set).

- [ ] **Step 7: Commit**

```bash
git add noSQL/mongodb/docker/docker-compose.yml noSQL/mongodb/docker/prometheus/prometheus.yml noSQL/mongodb/docker/grafana/provisioning/datasources/prometheus.yml
git commit -m "$(cat <<'EOF'
feat(nosql): add mongodb-exporter and Prometheus metrics pipeline

mongodb-exporter polls the existing 3-node replica set over the
MongoDB wire protocol (same hostnames the app and mongo-express
already use) and exposes Prometheus-format metrics; Prometheus
scrapes it every 15s. No changes to the mongo1/mongo2/mongo3 service
definitions. Host port 9095 (not 9090/9091) so this can run alongside
RabbitMQ's and Kafka's monitoring stacks.
EOF
)"
```

---

### Task 2: Grafana dashboard

**Files:**
- Modify: `noSQL/mongodb/docker/docker-compose.yml`
- Create: `noSQL/mongodb/docker/grafana/provisioning/dashboards/provider.yml`
- Create: `noSQL/mongodb/docker/grafana/dashboards/mongodb.json`

**Interfaces:**
- Consumes: the running `prometheus`/`mongodb-exporter` pipeline from Task 1, and the real metric names discovered in Task 1 Step 6.
- Produces: a Grafana instance at `http://localhost:3002` with a pre-loaded "MongoDB Demo Cluster" dashboard. Task 4's verification depends on this.

- [ ] **Step 1: Add the `grafana` service**

In `noSQL/mongodb/docker/docker-compose.yml`, immediately after the `prometheus` block (added in Task 1), insert:

```yaml
  grafana:
    image: grafana/grafana:11.1.0
    container_name: mongodb-grafana
    ports:
      - "3002:3000"
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
      - mongo_network
```

- [ ] **Step 2: Create the dashboard provider config**

Create `noSQL/mongodb/docker/grafana/provisioning/dashboards/provider.yml`:

```yaml
apiVersion: 1

providers:
  - name: MongoDB
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] **Step 3: Create the dashboard JSON**

Create `noSQL/mongodb/docker/grafana/dashboards/mongodb.json`. This uses the most common `percona/mongodb_exporter` metric names as a starting point — Step 5 below verifies each panel against what Task 1 Step 6 actually discovered and fixes any mismatches:

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
      "title": "Replica Set Member State",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "mongodb_rs_members_state",
          "legendFormat": "{{member_idx}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "title": "Replication Lag (seconds)",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "s" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "max(mongodb_rs_members_optimeDate) - min(mongodb_rs_members_optimeDate)",
          "legendFormat": "Lag",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "title": "Op Counters",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 8 },
      "fieldConfig": { "defaults": { "unit": "ops" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum by (type) (rate(mongodb_op_counters_total[1m]))",
          "legendFormat": "{{type}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 4,
      "title": "Active Connections",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 8 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "mongodb_connections",
          "legendFormat": "{{state}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "title": "Resident Memory (MB)",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 16 },
      "fieldConfig": { "defaults": { "unit": "decmbytes" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "mongodb_memory{type=\"resident\"}",
          "legendFormat": "Resident",
          "refId": "A"
        }
      ]
    },
    {
      "id": 6,
      "title": "Document Counts (products / orders)",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 16 },
      "fieldConfig": { "defaults": { "unit": "short" }, "overrides": [] },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "mongodb_collstats_count{database=\"ecommerce\"}",
          "legendFormat": "{{collection}}",
          "refId": "A"
        }
      ]
    }
  ],
  "refresh": "10s",
  "schemaVersion": 39,
  "tags": ["mongodb"],
  "templating": { "list": [] },
  "time": { "from": "now-15m", "to": "now" },
  "timepicker": {},
  "timezone": "browser",
  "title": "MongoDB Demo Cluster",
  "uid": "mongodb-demo-cluster",
  "version": 1
}
```

- [ ] **Step 4: Validate the JSON and bring Grafana up**

Run: `python3 -m json.tool noSQL/mongodb/docker/grafana/dashboards/mongodb.json > /dev/null && echo "valid JSON"`
Expected: `valid JSON`

Run:
```bash
cd noSQL/mongodb/docker
docker compose up -d
```
Wait ~15 seconds, then check:
```bash
docker compose ps
```
Expected: `grafana` running (in addition to everything from Task 1).

- [ ] **Step 5: Confirm the dashboard loaded, then verify and fix each panel's query**

Run:
```bash
curl -s -u admin:admin http://localhost:3002/api/search?query=MongoDB
```
Expected: JSON array containing one object with `"title":"MongoDB Demo Cluster"`.

Now verify each panel's query actually returns data, using Prometheus's query API directly (substitute each `expr` from Step 3):
```bash
curl -s "http://localhost:9095/api/v1/query?query=mongodb_rs_members_state" | python3 -m json.tool
curl -s "http://localhost:9095/api/v1/query?query=mongodb_op_counters_total" | python3 -m json.tool
curl -s "http://localhost:9095/api/v1/query?query=mongodb_connections" | python3 -m json.tool
curl -s "http://localhost:9095/api/v1/query?query=mongodb_memory" | python3 -m json.tool
curl -s "http://localhost:9095/api/v1/query?query=mongodb_collstats_count" | python3 -m json.tool
```
Expected for each: `"status":"success"` with a non-empty `"result"` array. **If any query returns an empty `"result"` array**, the metric name doesn't match this exporter version — go back to Task 1 Step 6's discovered metric list, find the real name for that concept (e.g. if `mongodb_connections` doesn't exist, look for `mongodb_ss_connections_current`/`mongodb_ss_connections_available` instead), update that panel's `expr` in `mongodb.json` to match, and re-run `docker compose up -d` to reload Grafana's provisioned dashboard. Repeat until all 6 panels query successfully.

- [ ] **Step 6: Commit**

```bash
git add noSQL/mongodb/docker/docker-compose.yml noSQL/mongodb/docker/grafana/provisioning/dashboards/provider.yml noSQL/mongodb/docker/grafana/dashboards/mongodb.json
git commit -m "$(cat <<'EOF'
feat(nosql): add Grafana with pre-loaded MongoDB Demo Cluster dashboard

Six panels: replica set member state, replication lag, op counters,
active connections, resident memory, and document counts for
products/orders. Host port 3002 (not 3000/3001) so this can run
alongside RabbitMQ's and Kafka's Grafana instances. Panel queries
verified against the live exporter and corrected to match its actual
metric names where they differed from the initial best-effort guess.
EOF
)"
```

---

### Task 3: Document the monitoring stack in the MongoDB README

**Files:**
- Modify: `noSQL/mongodb/README.md`

**Interfaces:**
- Consumes: the URLs and ports fixed in Tasks 1-2 (`http://localhost:9095`, `http://localhost:3002`, admin/admin).
- Produces: nothing consumed by later tasks — documentation only.

- [ ] **Step 1: Add the URLs to "Start the cluster"**

In `noSQL/mongodb/README.md`, find this line:

```markdown
mongo-express: http://localhost:8091
```

Replace it with:

```markdown
mongo-express: http://localhost:8091

Grafana: http://localhost:3002 (admin/admin)
Prometheus: http://localhost:9095
```

- [ ] **Step 2: Add a "Monitoring" section before "Replica set admin commands"**

Find this exact block:

```markdown
## Replica set admin commands
```

Replace it with:

```markdown
## Monitoring

`mongodb-exporter` polls the replica set over the MongoDB wire protocol (the same hostnames the app and mongo-express already use) and exposes Prometheus-format metrics — no changes to the `mongo1`/`mongo2`/`mongo3` containers themselves. Prometheus scrapes it every 15 seconds; Grafana visualizes the result.

| URL | Purpose |
|---|---|
| http://localhost:9095 | Prometheus — query metrics directly, check scrape target health under `/targets` |
| http://localhost:3002 | Grafana (admin/admin) — pre-loaded "MongoDB Demo Cluster" dashboard |

**Dashboard panels:** replica set member state, replication lag, op counters (ties to the CRUD pattern), active connections, resident memory, document counts for `products`/`orders` (ties to the demo's domain data growth).

## Replica set admin commands
```

- [ ] **Step 3: Commit**

```bash
git add noSQL/mongodb/README.md
git commit -m "$(cat <<'EOF'
docs(nosql): document the MongoDB Prometheus/Grafana monitoring stack

Adds the Grafana/Prometheus URLs to the cluster startup section and a
new Monitoring section describing the mongodb-exporter metrics
pipeline and the six dashboard panels.
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
cd noSQL/mongodb/docker
docker compose ps
```
Expected: `mongo1`, `mongo2`, `mongo3` healthy; `mongo-express`, `mongodb-exporter`, `prometheus`, `grafana` running. If anything is missing, run `docker compose up -d` again and re-check.

- [ ] **Step 2: Confirm the Prometheus scrape target is up**

```bash
curl -s http://localhost:9095/api/v1/targets | python3 -c "import json,sys; d=json.load(sys.stdin); print([t['health'] for t in d['data']['activeTargets']])"
```
Expected: `['up']` (or a list containing `'up'`).

- [ ] **Step 3: Generate MongoDB traffic via the demo app**

```bash
cd ../spring-demo
mvn spring-boot:run > /tmp/mongo-app.log 2>&1 &
sleep 20
grep -i "Started MongoDbDemoApplication" /tmp/mongo-app.log
PRODUCT=$(curl -s -X POST "http://localhost:8084/demo/products" -H "Content-Type: application/json" -d '{"name":"Monitoring Test Widget","price":5.0,"stock":50}')
echo "$PRODUCT"
PRODUCT_ID=$(echo "$PRODUCT" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
curl -s -X POST "http://localhost:8084/demo/orders" -H "Content-Type: application/json" -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}"
```
Expected: a line containing `Started MongoDbDemoApplication`, a product JSON with an `id`, and an order JSON with `status: "PLACED"`.

- [ ] **Step 4: Confirm the dashboard shows real data**

```bash
curl -s "http://localhost:9095/api/v1/query?query=mongodb_rs_members_state" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d['data']['result']))"
```
Expected: `3` (one series per replica set member). If this doesn't match, re-check Task 2 Step 5's verification — the panel's metric name may need correcting.

```bash
curl -s "http://localhost:9095/api/v1/query?query=$(python3 -c "import urllib.parse; print(urllib.parse.quote('mongodb_collstats_count{database=\"ecommerce\",collection=\"products\"}'))")" | python3 -m json.tool
```
Expected: `"status":"success"` with at least one result whose value is `>= 1` (the product created in Step 3).

- [ ] **Step 5: Stop the app and tear down**

```bash
pkill -f "MongoDbDemoApplication" 2>/dev/null; pkill -f "spring-boot:run" 2>/dev/null
cd ../docker
docker compose down
```

If any step in this task surfaced a problem, go back and fix the relevant file in Tasks 1-3, re-commit, and re-run verification from Step 1.
