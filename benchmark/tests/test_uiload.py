from kbench.uiload import MONITOR_TYPES, api_paths, ui_paths


def test_ui_paths_cover_every_monitor_list():
    paths = dict(ui_paths(http_monitor_id=7))
    for monitor_type in MONITOR_TYPES:
        assert paths[f"ui_{monitor_type}_monitors"] == f"/{monitor_type}-monitors"
        assert paths[f"ui_{monitor_type}_monitors_list"] == f"/{monitor_type}-monitors/fragments/list"
    assert paths["ui_dashboard_fragment"] == "/fragments/dashboard"
    assert paths["ui_http_monitor_details"] == "/http-monitors/7"
    assert paths["ui_http_monitor_incidents"] == "/http-monitors/fragments/details-uptime-incidents/7"


def test_ui_paths_without_a_monitor():
    assert not any("details" in path for _, path in ui_paths(http_monitor_id=None))


def test_api_paths():
    assert dict(api_paths())["api_dns_monitors"] == "/api/v2/dns-monitors"
    assert len(api_paths()) == len(MONITOR_TYPES)
