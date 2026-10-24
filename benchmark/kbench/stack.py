"""Thin wrapper around `docker compose` for the benchmark stack, translating a resource profile into the environment
variables of compose.yml."""

import json
import os
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path

import httpx
import psycopg
import yaml

ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = ROOT / "compose.yml"
KUVASZ_URL = "http://127.0.0.1:18080"
API_KEY = "kbench-api-key-0123456789"
PG_DSN = "host=127.0.0.1 port=55432 dbname=postgres user=kuvasz password=kuvasz options=-csearch_path=kuvasz"
KUVASZ_CONTAINER = "kb-kuvasz"
POSTGRES_CONTAINER = "kb-postgres"
TARGET_SERVICES = [
    "openresty-a",
    "openresty-b",
    "coredns-a",
    "coredns-b",
    "work-healthy-1",
    "work-healthy-2",
    "work-healthy-3",
    "work-unhealthy",
    "work-flapping",
    "work-stopped",
]
GROUP_B_CONTAINERS = ["kb-openresty-b", "kb-coredns-b"]
NETWORK = "kbench"


def load_profiles() -> dict:
    return yaml.safe_load((ROOT / "config" / "profiles.yml").read_text())


@dataclass(frozen=True)
class StackSettings:
    profile: str
    heap: str
    kuvasz_tag: str
    gc_log_dir: Path
    latency_retention_days: int = 7
    db_pool_size: int = 10

    def env(self, profiles: dict) -> dict:
        profile = profiles["profiles"][self.profile]
        kuvasz, postgres = profile["kuvasz"], profile["postgres"]
        java_opts = f"{profiles['heap_modes'][self.heap]} {profiles['jvm_common']}"
        return {
            "KUVASZ_TAG": self.kuvasz_tag,
            "KUVASZ_CPUS": str(kuvasz["cpus"]),
            "KUVASZ_MEM": kuvasz["mem"],
            "KUVASZ_JAVA_OPTS": java_opts,
            "KUVASZ_GC_LOG_DIR": str(self.gc_log_dir),
            "KUVASZ_LATENCY_RETENTION_DAYS": str(self.latency_retention_days),
            "KUVASZ_DB_POOL_SIZE": str(self.db_pool_size),
            "PG_CPUS": str(postgres["cpus"]),
            "PG_MEM": postgres["mem"],
            "PG_SHARED_BUFFERS": postgres["shared_buffers"],
            "PG_EFFECTIVE_CACHE_SIZE": postgres["effective_cache_size"],
            "TARGET_HTTP_CPUS": str(profiles["targets"]["http_cpus"]),
            "TARGET_DNS_CPUS": str(profiles["targets"]["dns_cpus"]),
        }


class Stack:
    def __init__(self, settings: StackSettings, profiles: dict | None = None):
        self.settings = settings
        self.env = {**os.environ, **settings.env(profiles or load_profiles())}

    def compose(self, *args: str, check: bool = True) -> subprocess.CompletedProcess:
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), *args], env=self.env, capture_output=True, text=True
        )
        if check and result.returncode != 0:
            raise RuntimeError(f"docker compose {' '.join(args)} failed:\n{result.stderr}")
        return result

    def reset(self):
        """Tears everything down, including the database volume."""
        self.compose("down", "--volumes", "--remove-orphans", "--timeout", "10")

    def up_dependencies(self):
        self.settings.gc_log_dir.mkdir(parents=True, exist_ok=True)
        # The unhealthy and the flapping workloads never pass --wait, that's their whole point
        self.compose("up", "-d", *[s for s in TARGET_SERVICES if s != "work-stopped"])
        self.compose("up", "-d", "--wait", "postgres", "work-healthy-1", "work-healthy-2", "work-healthy-3")
        # Created once and left exited, that's the state the docker monitors look for
        self.compose("up", "-d", "--no-deps", "work-stopped")

    def start_kuvasz(self, timeout: float = 600) -> float:
        """(Re)creates the Kuvasz container with the current config, and returns the seconds it took to become healthy."""
        self.settings.gc_log_dir.mkdir(parents=True, exist_ok=True)
        self.compose("rm", "--stop", "--force", "kuvasz")
        started = time.monotonic()
        self.compose("up", "-d", "--no-deps", "kuvasz")
        return wait_for_health(started, timeout)

    def stop_kuvasz(self):
        self.compose("stop", "--timeout", "30", "kuvasz", check=False)

    def resize_postgres(self, cpus: float, mem: str):
        """Changes the limits of the running database container, e.g. to load a long history faster than its profile
        would allow. Restarted afterwards, so it starts with an empty cache, like after any restart of the box."""
        subprocess.run(["docker", "update", "--cpus", str(cpus), "--memory", mem, "--memory-swap", mem,
                        POSTGRES_CONTAINER], check=True, capture_output=True)
        subprocess.run(["docker", "restart", POSTGRES_CONTAINER], check=True, capture_output=True)
        self.compose("up", "-d", "--wait", "postgres")

    def kuvasz_logs(self) -> str:
        return subprocess.run(
            ["docker", "logs", KUVASZ_CONTAINER], capture_output=True, text=True, check=False
        ).stdout

    def disconnect_group_b(self):
        for container in GROUP_B_CONTAINERS:
            subprocess.run(["docker", "network", "disconnect", NETWORK, container], check=True)

    def reconnect_group_b(self):
        ips = {"kb-openresty-b": "172.31.89.11", "kb-coredns-b": "172.31.89.21"}
        for container in GROUP_B_CONTAINERS:
            subprocess.run(
                ["docker", "network", "connect", "--ip", ips[container], NETWORK, container], check=True
            )


def wait_for_health(started: float, timeout: float) -> float:
    deadline = started + timeout
    while time.monotonic() < deadline:
        if not container_running(KUVASZ_CONTAINER):
            raise RuntimeError("Kuvasz exited while starting up")
        try:
            if httpx.get(f"{KUVASZ_URL}/api/v2/health", timeout=2).status_code == 200:
                return time.monotonic() - started
        except httpx.HTTPError:
            pass
        time.sleep(0.5)
    raise TimeoutError("Kuvasz didn't become healthy in time")


def container_running(name: str) -> bool:
    result = subprocess.run(
        ["docker", "inspect", "--format", "{{.State.Running}}", name], capture_output=True, text=True, check=False
    )
    return result.stdout.strip() == "true"


def container_state(name: str) -> dict:
    result = subprocess.run(
        ["docker", "inspect", "--format", "{{json .State}}", name], capture_output=True, text=True, check=True
    )
    return json.loads(result.stdout)


def db() -> psycopg.Connection:
    return psycopg.connect(PG_DSN, autocommit=True)
