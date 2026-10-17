import json
from collections import Counter
from pathlib import Path

import pytest
import yaml

from kbench import generate as g

MIXES = sorted(p.stem for p in (g.CONFIG_DIR / "mixes").glob("*.yml"))


@pytest.mark.parametrize("mix_name", MIXES)
def test_every_mix_covers_every_monitor_type_with_exact_shares(mix_name):
    mix = g.load_mix(mix_name)
    for scale in (1, 3):
        counts = Counter(m.type for m in g.generate(mix, scale))
        assert counts == Counter({t: share * scale for t, share in mix["types"].items()})
        assert set(counts) == set(g.TYPES)


def test_a_smaller_set_is_a_prefix_of_a_bigger_one():
    mix = g.load_mix("balanced")
    small = {m.name: m.config for m in g.generate(mix, 2)}
    big = {m.name: m.config for m in g.generate(mix, 10)}
    assert small.items() <= big.items()


def test_distributions_are_hit_closely_at_scale():
    mix = g.load_mix("balanced")
    monitors = g.generate(mix, 100)
    for monitor_type in g.TYPES:
        of_type = [m for m in monitors if m.type == monitor_type]
        for attribute, weights in (
            ("behavior", mix["behaviors"][monitor_type]),
            ("interval", mix["intervals"][monitor_type]),
        ):
            total = sum(weights.values())
            counts = Counter(getattr(m, attribute) for m in of_type)
            for value, weight in weights.items():
                share = counts[type(next(iter(counts)))(value)] / len(of_type)
                assert share == pytest.approx(weight / total, abs=0.01), (monitor_type, attribute, value)


def test_rare_behaviors_are_present_even_in_the_smallest_set():
    monitors = g.generate(g.load_mix("balanced"), 1)
    behaviors = {(m.type, m.behavior) for m in monitors}
    assert ("http", "healthy") in behaviors
    assert ("http", "down") in behaviors
    assert any(m.expected == "DOWN" for m in monitors)


def test_group_b_gets_its_share_of_the_grouped_targets():
    monitors = [m for m in g.generate(g.load_mix("balanced"), 50) if m.group is not None]
    share_b = sum(m.group == "b" for m in monitors) / len(monitors)
    assert share_b == pytest.approx(0.30, abs=0.02)


def test_targets_match_the_behaviors():
    monitors = g.generate(g.load_mix("balanced"), 5)
    for m in monitors:
        c = m.config
        match m.type, m.behavior:
            case "http", "healthy":
                assert c["url"].endswith("/ok")
            case "http", "timeout":
                assert c["url"].endswith("/hang")
            case "http", "flapping":
                assert "/flap/" in c["url"]
            case "tcp", "down":
                assert c["port"] == 9
            case ("tcp" | "icmp"), "timeout":
                assert c["host"] in g.UNUSED_IPS
            case "dns", "down":
                assert ".missing." in c["host"]
            case "dns", "timeout":
                assert c["resolver-port"] == 2053
            case "docker", "down":
                assert c["container"] in ("kb-work-stopped", "kb-work-unhealthy")
        if m.group is not None and m.type in ("http", "tcp", "dns"):
            target = c.get("url", "") + c.get("host", "") + c.get("resolver-host", "")
            assert any(host in target for host in g.GROUP_HOSTS[m.group].values())


def test_http_features():
    monitors = [m for m in g.generate(g.load_mix("balanced"), 20) if m.type == "http"]
    for m in monitors:
        if m.config.get("ssl-check-enabled"):
            assert m.config["url"].startswith("https://")
        # a HEAD response has no body to look for the keyword in
        assert not ("expected-keyword" in m.config and m.config.get("request-method") == "HEAD")
    with_keyword = sum("expected-keyword" in m.config for m in monitors) / len(monitors)
    assert with_keyword == pytest.approx(0.20, abs=0.02)


def test_push_clients():
    monitors = [m for m in g.generate(g.load_mix("balanced"), 10) if m.type == "push"]
    secrets = [m.push["secret"] for m in monitors]
    assert len(set(secrets)) == len(secrets)
    for m in monitors:
        assert len(m.push["secret"]) >= 36
        assert m.push["secret"] == m.config["client-secret"]
        assert 0.1 * m.interval - 1 <= m.push["grace"] <= 0.2 * m.interval + 1


def test_unbalanced_mix_is_rejected(tmp_path, monkeypatch):
    (tmp_path / "mixes").mkdir()
    (tmp_path / "distributions.yml").write_text((g.CONFIG_DIR / "distributions.yml").read_text())
    (tmp_path / "mixes" / "broken.yml").write_text("types: { http: 50, push: 10 }")
    monkeypatch.setattr(g, "CONFIG_DIR", tmp_path)
    with pytest.raises(ValueError, match="add up to"):
        g.load_mix("broken")


def test_write_step(tmp_path: Path):
    summary = g.write_step(tmp_path, "balanced", 2)
    config = yaml.safe_load((tmp_path / "kuvasz.yml").read_text())
    expectations = json.loads((tmp_path / "expectations.json").read_text())
    pushers = json.loads((tmp_path / "pushers.json").read_text())

    real_slice = g.load_real_slice()
    real_count = sum(len(v) for v in real_slice.values())
    assert summary["stub_monitors"] == 200
    assert summary["real_slice_monitors"] == real_count
    assert sum(len(config[g.YAML_KEYS[t]]) for t in g.TYPES) == 200 + real_count
    assert len(expectations) == 200 + real_count
    assert len(pushers) == summary["by_type"]["push"]["count"]
    assert config["micronaut"]["metrics"]["binders"]["jvm"]["enabled"] is True
    assert config["docker-hosts"][0]["name"] == g.DOCKER_HOST_NAME
    assert expectations["real-github"]["behavior"] == "real"
    assert summary["checks_per_second"] > 0


def test_write_step_without_the_real_slice(tmp_path: Path):
    summary = g.write_step(tmp_path, "homelab", 1, with_real_slice=False)
    config = yaml.safe_load((tmp_path / "kuvasz.yml").read_text())
    assert summary["real_slice_monitors"] == 0
    assert sum(len(config[g.YAML_KEYS[t]]) for t in g.TYPES) == 100
