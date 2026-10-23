import itertools
import random

import pytest

from kbench.pusher import FIRST_HEARTBEAT_SPREAD, JITTER, schedule, send_times


def delays(behavior: str, interval=60, grace=10, count=7) -> list[float]:
    return list(itertools.islice(schedule(behavior, interval, grace, random.Random(1)), count))


@pytest.mark.parametrize("behavior", ["healthy", "slow", "flapping", "down"])
def test_first_heartbeat_is_spread(behavior):
    assert 0 <= delays(behavior)[0] <= FIRST_HEARTBEAT_SPREAD


def test_healthy_clients_stay_within_the_grace_period():
    for delay in delays("healthy", count=50)[1:]:
        assert abs(delay - 60) <= JITTER * 10


def test_slow_clients_are_late_but_within_grace():
    for delay in delays("slow")[1:]:
        assert 60 < delay < 70


def test_flapping_clients_skip_every_other_heartbeat():
    later = delays("flapping")[1:]
    assert later[0] > 60 * 2 + 10  # one heartbeat skipped, beyond its grace period too
    assert abs(later[1] - 60) <= JITTER * 10
    assert later[2] > 60 * 2 + 10


def test_down_clients_die_after_their_first_heartbeat():
    assert len(delays("down")) == 1


def test_send_times_keep_the_schedule():
    assert list(send_times([5.0, 60.0, 61.5], start=100.0)) == [105.0, 165.0, 226.5]
