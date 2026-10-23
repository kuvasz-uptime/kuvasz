from pathlib import Path

from kbench.run import Block, midpoint
from kbench.stack import StackSettings, load_profiles


def steps(*outcomes: tuple[int, bool]) -> list[dict]:
    return [{"scale": scale, "passed": passed, "failures": []} for scale, passed in outcomes]


def test_midpoint():
    assert midpoint(steps((1, True), (5, True), (15, True), (40, False))) == 27
    assert midpoint(steps((15, True), (40, False), (27, True))) == 33
    assert midpoint(steps((1, True), (5, True))) is None  # nothing failed
    assert midpoint(steps((1, False))) is None  # nothing passed
    assert midpoint(steps((1, True), (2, False))) is None  # no room in between


def test_block_from_config():
    defaults = {"mix": "balanced", "heap": "ram75", "virtual_threads": False}
    block = Block.from_config({"name": "knee-M", "kind": "knee", "profile": "M", "heap": "stock"}, defaults, (40,))
    assert block == Block("knee-M", "knee", "M", "balanced", "stock", False, (40,))


def test_block_from_config_with_ui_users():
    defaults = {"mix": "balanced", "heap": "ram75", "virtual_threads": True}
    config = {"name": "uiload-XS", "kind": "uiload", "profile": "XS", "heap": "stock", "scale": 15, "users": 5}
    block = Block.from_config(config, defaults, (15,))
    assert block.ui_users == 5
    assert block.kind == "uiload"


def test_block_from_config_with_a_bigger_connection_pool():
    defaults = {"mix": "balanced", "heap": "ram75", "virtual_threads": True}
    config = {"name": "uiload-S", "kind": "uiload", "profile": "S1536", "heap": "xmx768", "db_pool_size": 30}
    assert Block.from_config(config, defaults, (50,)).db_pool_size == 30
    assert Block.from_config({**config, "db_pool_size": 10}, defaults, (50,)).db_pool_size == 10


def test_the_connection_pool_reaches_the_stack():
    settings = StackSettings("S1536", "xmx768", True, "dev", Path("gc"), db_pool_size=30)
    assert settings.env(load_profiles())["KUVASZ_DB_POOL_SIZE"] == "30"
