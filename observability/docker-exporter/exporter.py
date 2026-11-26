import http.client
import json
import socket
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import quote

snapshot = ""
snapshot_lock = threading.Lock()


class DockerConnection(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(4)
        self.sock.connect("/var/run/docker.sock")


def docker_get(path):
    connection = DockerConnection("localhost", timeout=4)
    connection.request("GET", path, headers={"Host": "docker"})
    response = connection.getresponse()
    body = response.read()
    connection.close()
    if response.status != 200:
        raise RuntimeError(f"Docker API returned HTTP {response.status}")
    return json.loads(body)


def label(value):
    return str(value).replace("\\", "\\\\").replace("\n", "\\n").replace('"', '\\"')


def metric(name, labels, value):
    label_text = ",".join(f'{key}="{label(item)}"' for key, item in labels.items())
    return f"{name}{{{label_text}}} {value}"


def collect():
    lines = [
        "# HELP docker_exporter_up Whether Docker API metrics were collected successfully.",
        "# TYPE docker_exporter_up gauge",
    ]
    try:
        containers = docker_get("/v1.41/containers/json?all=1&size=1")
        running = []
        for container in containers:
            labels = container.get("Labels") or {}
            service = labels.get("com.docker.compose.service")
            if not service:
                continue

            container_id = container["Id"]
            names = container.get("Names") or [container_id[:12]]
            name = names[0].lstrip("/")
            image = container.get("Image", "")
            common = {"service": service, "name": name, "image": image}
            state = container.get("State", "unknown")
            lines.append(metric("docker_container_state", {**common, "state": state}, 1))
            lines.append(metric("docker_container_info", common, 1))
            lines.append(metric("docker_container_restarts_total", common, container.get("RestartCount", 0)))
            size = container.get("SizeRw")
            if size is not None:
                lines.append(metric("docker_container_writable_layer_bytes", common, size))
            if state != "running":
                continue
            running.append((container_id, common))

        errors = 0
        with ThreadPoolExecutor(max_workers=16) as pool:
            futures = {
                pool.submit(collect_container_stats, container_id, common): common
                for container_id, common in running
            }
            for future in as_completed(futures):
                try:
                    lines.extend(future.result())
                except Exception:
                    errors += 1
        lines.append("docker_exporter_up 1")
        lines.append(f"docker_exporter_container_errors {errors}")
    except Exception as error:
        print(f"Docker metrics collection failed: {error}", flush=True)
        lines.append("docker_exporter_up 0")
    return "\n".join(lines) + "\n"


def collect_container_stats(container_id, common):
    stats = docker_get(f"/v1.41/containers/{quote(container_id, safe='')}/stats?stream=false")
    lines = []
    cpu = stats.get("cpu_stats") or {}
    previous_cpu = stats.get("precpu_stats") or {}
    cpu_usage = (cpu.get("cpu_usage") or {}).get("total_usage", 0)
    previous_usage = (previous_cpu.get("cpu_usage") or {}).get("total_usage", 0)
    system_usage = cpu.get("system_cpu_usage", 0)
    previous_system = previous_cpu.get("system_cpu_usage", 0)
    online_cpus = cpu.get("online_cpus") or len((cpu.get("cpu_usage") or {}).get("percpu_usage") or []) or 1
    cpu_delta = max(0, cpu_usage - previous_usage)
    system_delta = max(0, system_usage - previous_system)
    cpu_cores = cpu_delta / system_delta * online_cpus if system_delta else 0
    lines.append(metric("docker_container_cpu_cores", common, cpu_cores))

    memory = stats.get("memory_stats") or {}
    memory_usage = memory.get("usage", 0)
    memory_cache = (memory.get("stats") or {}).get("inactive_file", 0)
    lines.append(metric("docker_container_memory_working_set_bytes", common, max(0, memory_usage - memory_cache)))
    lines.append(metric("docker_container_memory_limit_bytes", common, memory.get("limit", 0)))

    networks = (stats.get("networks") or {}).values()
    lines.append(metric(
        "docker_container_network_receive_bytes_total",
        common,
        sum(interface.get("rx_bytes", 0) for interface in networks),
    ))
    lines.append(metric(
        "docker_container_network_transmit_bytes_total",
        common,
        sum(interface.get("tx_bytes", 0) for interface in networks),
    ))
    return lines


def refresh_metrics():
    global snapshot
    while True:
        collected = collect()
        with snapshot_lock:
            snapshot = collected
        time.sleep(10)


class MetricsHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        with snapshot_lock:
            body = snapshot.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string, *args):
        return


threading.Thread(target=refresh_metrics, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 9400), MetricsHandler).serve_forever()
