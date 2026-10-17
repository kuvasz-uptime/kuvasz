from kbench.analyze import due_for_down
from kbench.smoke import due_after, evaluate


def expectation(monitor_type="http", expected="UP", interval=60, threshold=1, behavior="healthy"):
    return {"type": monitor_type, "expected": expected, "interval": interval, "threshold": threshold,
            "behavior": behavior, "group": "a"}


def test_due_after():
    assert due_after(expectation(expected="UP", interval=60), 0) == 90
    assert due_after(expectation("push", expected="UP"), 6) == 30
    # a failing monitor has the same deadline as in the gate of the benchmark
    for e in (expectation(expected="DOWN", interval=60, threshold=2), expectation("push", expected="DOWN")):
        assert due_after(e, 6) == due_for_down(e, 6)


def test_evaluate():
    expectations = {
        "up-ok": expectation(),
        "up-but-down": expectation(),
        "down-ok": expectation(expected="DOWN"),
        "never-checked": expectation(),
        "not-due": expectation(interval=3600),
        "flapping": expectation(expected=None, behavior="flapping"),
        "push-expired": expectation("push", interval=60),
    }
    statuses = {
        "up-ok": {"status": "UP", "error": None},
        "up-but-down": {"status": "DOWN", "error": "boom"},
        "down-ok": {"status": "DOWN", "error": "503"},
        "flapping": {"status": "DOWN", "error": None},
        "push-expired": {"status": "DOWN", "error": None},
    }
    result = evaluate(expectations, statuses, elapsed=400, push_graces={"push-expired": 6})
    assert sorted(result["ok"]) == ["down-ok", "up-ok"]
    assert sorted(result["mismatch"]) == [("never-checked", "UP", None, None), ("up-but-down", "UP", "DOWN", "boom")]
    assert sorted(result["not_due"]) == ["not-due", "push-expired"]
    assert result["no_expectation"] == ["flapping"]
