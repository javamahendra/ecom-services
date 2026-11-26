# Local observability guide

This guide explains how logs, metrics, and distributed traces move through the
local e-commerce observability stack, how to find a request or order, and how to
build and verify Grafana dashboards.

## What each component does

```text
Spring services
  +-- Micrometer metrics -- HTTP scrape /actuator/prometheus --> Prometheus --+
  +-- application log files --> Grafana Alloy --> Loki -----------------------+
  +-- OpenTelemetry traces -- OTLP HTTP/gRPC --> Collector --> Tempo ----------+
                                                                               v
                                                                             Grafana
```

- **Grafana** is the UI for dashboards, Explore, and links between the three
  data sources. Grafana queries the data sources; it is not where raw
  application telemetry is collected.
- **Prometheus** collects numeric time-series metrics by scraping each
  service's `/actuator/prometheus` endpoint every 15 seconds. Use it for rates,
  totals, latency, errors, and service health.
- **Grafana Alloy** tails `logs/*.log`, extracts selected fields, and sends log
  entries to Loki. Logs include application messages and request-completion
  entries.
- **Loki** stores log lines and indexes a small set of labels, including
  `service`, `method`, and `status`. Trace and span IDs are stored as structured
  metadata when they are present. Search log contents with LogQL rather than
  making high-cardinality values such as IDs into Loki labels.
- **OpenTelemetry Collector** receives OTLP traces on ports 4317 (gRPC) and
  4318 (HTTP), batches them, and exports them to Tempo. This pipeline is for
  traces, not metrics.
- **Tempo** stores distributed traces: spans connected by a trace ID. A trace
  shows the participating services, operation names, durations, and parent /
  child relationships.

No application request depends on Grafana, Prometheus, Loki, Alloy, the
Collector, or Tempo being available.

## Run and check the stack

From the repository root in PowerShell:

```powershell
docker compose up -d
docker compose ps
```

Local interfaces:

| Component | Address | Check |
| --- | --- | --- |
| Grafana | <http://localhost:3001> | Open the dashboards and Explore |
| Prometheus | <http://localhost:9090> | Open `/targets`; service targets should be UP |
| Loki | <http://localhost:3100> | Query API; normally use Grafana |
| Tempo | <http://localhost:3200> | Trace API; normally use Grafana |
| OTLP HTTP receiver | `localhost:4318` | Applications send traces to `/v1/traces` |
| OTLP gRPC receiver | `localhost:4317` | Collector receiver |

The Grafana container's initial local-development credentials default to
`admin` / `admin` unless overridden with `GRAFANA_ADMIN_USER` and
`GRAFANA_ADMIN_PASSWORD`. Keep the stack bound to localhost; do not expose
these development credentials to an untrusted network.

Prometheus can scrape services launched from the IDE on host ports 8090-8097.
The services must be running for their targets to report UP. To diagnose a
target, open Prometheus `/targets`, inspect its last scrape error, then check
the corresponding service's `/actuator/health` and
`/actuator/prometheus` endpoints.

## How to follow a request

Open the provisioned **Service Logs** dashboard:

<http://localhost:3001/d/request-service-flow/request-service-flow>

Set a time range that includes the request. Use the filters as follows:

| Filter | What it finds | Typical scope |
| --- | --- | --- |
| **Trace ID** | Log entries containing a trace ID | All services participating in that distributed request |
| **Span ID** | Entries containing a span ID | The log events associated with that span; usually narrower than a trace |
| **Order ID** | Entries that mention the order ID | Order, inventory, dispatch, or notification events that log that ID |
| **User ID** | Entries that mention the user ID | Only service log entries that include that user ID |
| **Service** | Entries from selected services | One service or all services |
| **Level** | Entries whose text contains the selected log level | ERROR, WARN, INFO, DEBUG, or TRACE |
| **Search** | Any literal text in a log line | Exception text, endpoint, request ID, product ID, or another value |

The identifier filters use literal text matching against each log line. They
are combined with **AND**: use one identifier at a time to follow a flow.
Entering both an order ID and trace ID only shows individual log lines that
contain both values. General **Search** is also literal text, not a database
lookup or a structured field query.

Matching log lines are sorted oldest first to make the sequence easier to
follow. The activity chart shows matching log volume per service over the
selected time range; it is a useful overview, not a strict trace waterfall.
For exact span relationships and duration, expand a log entry and use its
**View trace** derived-field link, or open **Explore traces in Tempo**.

### Important limitations of identifier searches

- An identifier can only find entries that actually log it. For example, an
  order ID search may show order, inventory, and dispatch logs when those
  services emitted order events, but it does not guarantee every service
  handled that order.
- User IDs are not automatically attached to every log. A user ID search only
  returns log messages that include that value.
- Trace and span IDs appear only after the services have tracing enabled and
  restarted. Old log lines are not rewritten.
- A trace ID groups instrumented operations that share tracing context. It is
  not the same as an order ID: an order may trigger asynchronous Kafka work
  that has its own trace context, while the order ID remains the business
  correlation key.
- Trace/span values are structured metadata in Loki, not indexed stream
  labels. This avoids creating a new Loki stream for every request. The
  dashboard filters their text representation from the log line.
- A span ID is unique within a trace and is narrower than a trace ID. For a
  complete request path, search by trace ID.

For example, to follow an order:

1. Copy the order ID from the order details page or order log.
2. Select a time range that includes order creation and later asynchronous
   processing.
3. Paste the ID into **Order ID** and leave other identifier filters empty.
4. Read the log stream from oldest to newest and note which services appear.
5. To inspect an instrumented HTTP/Kafka trace, search its trace ID instead,
   expand a trace-bearing row, and open **View trace**.

## Metrics, logs, and traces are different signals

- **Metrics answer “how much / how often / how long?”** Use PromQL, for example
  `up{job="order-service"}` for target health or
  `rate(http_server_requests_seconds_count{job="order-service"}[5m])` for
  request rate.
- **Logs answer “what happened?”** Use LogQL, for example
  `{service="order-service"} |= "orderId="` or
  `{service="api-gateway",method="POST",status="200"}`.
- **Traces answer “where did this operation go and how long did each step
  take?”** Search in Tempo by trace ID or use TraceQL. A trace is composed of
  spans; logs and traces can be correlated with the trace ID.

Prometheus scrapes `/actuator/prometheus`; the services' OTLP metric export is
disabled. The Collector is configured with a traces pipeline only, so do not
configure the services to push metrics to its `/v1/metrics` endpoint unless a
metrics receiver and pipeline are added.

The gateway business counters (orders, login, registration, and admin user
creation) count successful requests observed since that gateway process
started. They are not database totals or counts reconstructed from historical
orders. Restarting the gateway resets those in-memory counters.

## Creating and maintaining dashboards

Dashboards in this repository are provisioned from JSON files. The
`dashboards.yml` provider loads JSON files from
`observability/grafana/provisioning/dashboards/` into the **E-commerce** folder.
The data sources are provisioned in
`observability/grafana/provisioning/datasources/datasources.yml` with stable
UIDs: `prometheus`, `loki`, and `tempo`.

Recommended workflow:

1. In Grafana, use **Dashboards → New → New dashboard** to prototype a panel.
   Choose a data source, use **Builder** for an initial query, then switch to
   **Code** to review and copy the PromQL, LogQL, or TraceQL.
2. Configure a meaningful title, description, unit, legend, and time range.
   Keep labels low-cardinality: service, method, status, and bounded operation
   names are appropriate; user, order, trace, and span IDs are not.
3. Export/download the dashboard JSON and save it under
   `observability/grafana/provisioning/dashboards/` with a descriptive
   filename and a unique dashboard `uid`.
4. Add or edit template variables under `templating.list` for filters. Use
   stable data source UIDs. Keep interpolated values escaped or use Grafana's
   appropriate variable format in queries.
5. For a datasource change, edit `datasources.yml`. Use Grafana's provisioned
   **derived fields** to link trace IDs in logs to Tempo; use Tempo's
   `tracesToLogsV2` configuration to search Loki logs from a trace.
6. Validate the JSON and patch before reloading:

   ```powershell
   Get-Content 'observability\grafana\provisioning\dashboards\my-dashboard.json' -Raw | ConvertFrom-Json
   git diff --check
   ```

7. Grafana polls provisioned dashboards, but for a deterministic local reload
   restart Grafana, then refresh the browser:

   ```powershell
   docker compose restart grafana
   ```

   Datasource provisioning changes also need Grafana to reload the provisioning
   configuration. Verify the dashboard opens in the expected folder and that
   its variables resolve.

Keep dashboard JSON in source control so it can be reviewed, reproduced, and
loaded in another environment. Avoid maintaining a UI-only dashboard that
doesn't have a matching provisioned JSON file.

## Test dashboards and data

Test a dashboard's entire path, not only whether the JSON parses:

1. **Check services and sources.** Confirm relevant Spring services are
   running, Prometheus targets are UP, Alloy and Loki are running, and the
   Collector and Tempo are running:

   ```powershell
   docker compose ps
   Invoke-RestMethod 'http://localhost:8090/actuator/health'
   Invoke-RestMethod 'http://localhost:8090/actuator/prometheus'
   ```

2. **Generate safe test traffic.** Browse public products or call a health
   endpoint to generate an HTTP trace. Avoid creating/cancelling real orders
   solely for a dashboard check unless using test data.
3. **Check metrics.** Open Prometheus `/targets`; query `up` or a known
   `http_server_requests_seconds_count` metric. If the panel is empty, check
   the target, query labels, and time range.
4. **Check logs.** In Grafana Explore select Loki and run
   `{service="api-gateway"}`. Confirm recent entries arrive. For request
   completions use `{service="api-gateway",method="GET",status="200"}`. Loki
   label selectors are case-sensitive.
5. **Check traces.** In Grafana Explore select Tempo and search recent traces,
   or open a trace link from a log. The trace should include a service name,
   root operation, and spans. Confirm the request logs also contain the
   matching trace ID after restarting services with the tracing log-filter
   configuration.
6. **Check dashboard filters.** Select a narrow time range, search a known
   value, and confirm the log panel and activity chart both change. Try one
   identifier filter at a time, then clear it and confirm the default view
   returns.
7. **Check cross-service flows.** Use a known order ID from existing logs and
   confirm each matching service appears. Not every order passes through every
   service, and an absent service may mean it did not log the ID rather than
   that the query is broken.
8. **Check provisioning.** Open the dashboard by UID or inspect it through the
   authenticated Grafana API:

   ```powershell
   $credential = Get-Credential
   $pair = '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
   $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
   Invoke-RestMethod 'http://localhost:3001/api/dashboards/uid/request-service-flow' `
     -Headers @{ Authorization = "Basic $basic" }
   ```

### Common reasons for "No data"

- The selected time range excludes the event.
- The service or observability container is stopped, or Prometheus target is
  DOWN.
- A label name/value does not match the live stream (for example
  `service="api-gateway"` vs a different label).
- A typed ID was not included in that service's log message.
- More than one identifier filter is populated, so the query requires all
  values on the same log line.
- Traces/log context were added after a service started; restart that service
  to load the configuration. Existing logs cannot gain trace IDs retroactively.
- A counter is process-local and its service restarted, resetting the value.

## Configuration and retention

| Purpose | Repository configuration |
| --- | --- |
| Prometheus targets and scrape interval | `observability/prometheus/prometheus.yml` |
| Log collection, labels, and structured metadata | `observability/alloy/config.alloy` |
| Loki storage and seven-day log retention | `observability/loki/loki.yml` |
| OTLP receive and export traces to Tempo | `observability/otel/collector.yml` |
| Tempo storage and three-day trace retention | `observability/tempo/tempo.yml` |
| Grafana data sources and trace/log correlation | `observability/grafana/provisioning/datasources/datasources.yml` |
| Grafana dashboard folder/provider | `observability/grafana/provisioning/dashboards/dashboards.yml` |
| Provisioned dashboards | `observability/grafana/provisioning/dashboards/*.json` |
| Application log output | Each service's `src/main/resources/application.properties` |
| Trace sampling and OTLP endpoint | Each service's `application.properties`; Compose environment in `docker-compose.yaml` |

Prometheus retains seven days of metrics, Loki seven days of logs, and Tempo
three days of traces. Data is stored in Docker named volumes. These are local
development defaults, not a production backup or access-control plan.
