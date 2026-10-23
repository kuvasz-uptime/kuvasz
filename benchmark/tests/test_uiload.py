import random

from kbench.uiload import (
    DEFAULT_STATUS_PAGE_MAX_MONITORS,
    DETAILS_REFRESH,
    LEGACY_INCIDENTS_PATH,
    MONITOR_TYPES,
    RARE_VISITS,
    Browser,
    Targets,
    api_poll,
    dashboard_visit,
    incidents_visit,
    monitor_details_visit,
    monitor_list_visit,
    parse_monitor_ids,
    rare_visits_of,
    refresh_schedule,
    status_pages_visit,
)

TARGETS = Targets(monitor_ids={"http": [7, 8], "docker": [9], "dns": [11], "push": [13]}, status_page_id=3,
                  status_page_slug="bench-status", monitors=5000)


CHARTS_PATH = "/api/internal/monitor-charts"


def paths_of(probes) -> dict[str, str]:
    return {probe.name: probe.path for probe in probes}


def test_the_dashboard_refreshes_its_fragment_like_the_browser():
    visit = dashboard_visit("P30D")
    assert paths_of(visit.probes) == {"ui_dashboard": "/?period=P30D",
                                      "ui_dashboard_fragment": "/fragments/dashboard?period=P30D"}
    assert [(r.interval, [p.name for p in r.probes]) for r in visit.refreshes] == [(30.0, ["ui_dashboard_fragment"])]
    assert paths_of(dashboard_visit(None).probes)["ui_dashboard"] == "/"


def test_a_monitor_list_refreshes_its_fragment():
    visit = monitor_list_visit("tcp")
    assert paths_of(visit.probes) == {"ui_tcp_monitors": "/tcp-monitors",
                                      "ui_tcp_monitors_list": "/tcp-monitors/fragments/list"}
    assert visit.refreshes[0].probes[0].htmx


def test_the_details_load_the_charts_with_the_default_period_then_with_the_picked_one():
    default = paths_of(monitor_details_visit("http", 7, None).probes)
    assert default["ui_http_monitor_details"] == "/http-monitors/7"
    assert default["ui_http_monitor_incidents"] == "/incidents/fragments/monitor/http/7"
    # The incidents of the chart come with its checks
    assert default["ui_http_monitor_chart_P1D"] == f"{CHARTS_PATH}/http/7?period=P1D"
    assert "ui_http_monitor_chart_P30D" not in default
    longer = paths_of(monitor_details_visit("http", 7, "P30D").probes)
    assert longer["ui_http_monitor_chart_P30D"] == f"{CHARTS_PATH}/http/7?period=P30D"
    assert "ui_http_monitor_chart_P1D" in longer


def test_the_details_refresh_their_heading_and_incidents_and_push_monitors_have_no_charts():
    visit = monitor_details_visit("dns", 11, None)
    assert visit.refreshes[0].interval == DETAILS_REFRESH
    assert {p.name for p in visit.refreshes[0].probes} == {
        "ui_dns_monitor_heading", "ui_dns_monitor_incidents", "ui_dns_monitor_snapshot"}
    assert not any("stats" in name for name in paths_of(monitor_details_visit("push", 13, "P30D").probes))


def test_the_details_of_an_image_without_the_incident_list():
    paths = paths_of(monitor_details_visit("http", 7, None, LEGACY_INCIDENTS_PATH).probes)
    assert paths["ui_http_monitor_incidents"] == "/http-monitors/fragments/details-uptime-incidents/7"


def test_the_incidents_use_the_picked_period():
    assert paths_of(incidents_visit("P30D").probes)["ui_incidents_list"] == "/incidents/fragments/list?period=P30D"
    assert paths_of(incidents_visit(None).probes)["ui_incidents"] == "/incidents"


def test_the_status_pages_and_the_default_one_above_its_limit():
    visit = status_pages_visit(TARGETS)
    assert paths_of(visit.probes)["ui_status_page_details"] == "/status-pages/3"
    assert paths_of(visit.probes)["ui_status_page_public"] == "/status/bench-status"
    statuses = {probe.name: probe.expected_status for probe in visit.probes}
    assert statuses["ui_default_status_page"] == 404
    assert visit.refreshes[0].interval == 60.0 and visit.refreshes[0].probes[0].htmx
    small = status_pages_visit(Targets(monitors=DEFAULT_STATUS_PAGE_MAX_MONITORS))
    assert {probe.name: probe.expected_status for probe in small.probes}["ui_default_status_page"] == 200
    assert "ui_status_page_details" not in paths_of(small.probes)


def test_refreshes_fire_while_the_page_is_read():
    visit = monitor_details_visit("http", 7, None)
    assert [at for at, _ in refresh_schedule(visit, 50.0)] == [15.0, 30.0, 45.0]
    assert refresh_schedule(dashboard_visit(None), 20.0) == []


def test_every_rare_page_is_opened_by_one_of_the_ui_users():
    assert sorted(kind for user in range(4) for kind in rare_visits_of(user, 4)) == sorted(RARE_VISITS)
    assert rare_visits_of(0, 1) == list(RARE_VISITS)


def test_a_browser_opens_its_rare_pages_among_its_first_visits_then_random_ones():
    browser = Browser(TARGETS, None, random.Random(1), ["export", "status_pages"])
    kinds = [browser.next_visit().kind for _ in range(30)]
    assert "export" in kinds[:6] and "status_pages" in kinds[:6]
    assert {"dashboard", "monitor_list", "monitor_details"} <= set(kinds)


def test_a_browser_with_a_picked_period_always_switches_the_charts_to_it():
    browser = Browser(TARGETS, "P30D", random.Random(2), [])
    details = [browser.visit("monitor_details") for _ in range(10)]
    assert all(any(p.name.endswith("_P30D") for p in v.probes) for v in details if not v.probes[0].path.startswith(
        "/push"))


def test_the_api_client_polls_the_v3_lists_and_the_heavier_figures_now_and_then():
    first = paths_of(api_poll(TARGETS, "P30D", 0))
    assert first["api_http_monitors"] == "/api/v3/http-monitors"
    assert first["api_incidents"] == "/api/v3/incidents"
    assert first["api_http_monitors_stats"] == "/api/v2/http-monitors/stats?period=P30D"
    assert first["api_status_page"] == "/api/v2/status-pages/3/details"
    assert {p.name: p.expected_status for p in api_poll(TARGETS, None, 0)}["api_default_status_page"] == 404
    second = paths_of(api_poll(TARGETS, None, 1))
    assert set(second) == {f"api_{MONITOR_TYPES[1]}_monitors", "api_incidents"}
    every_path = [p.path for poll in range(60) for p in api_poll(TARGETS, None, poll)]
    v2_lists = [path for path in every_path
                if path.startswith("/api/v2/") and path.split("?")[0].endswith(("-monitors", "incidents"))]
    assert v2_lists == []


def test_parse_monitor_ids():
    assert parse_monitor_ids("http=12:13,tcp=40") == {"http": [12, 13], "tcp": [40]}
    assert parse_monitor_ids("") == {}
