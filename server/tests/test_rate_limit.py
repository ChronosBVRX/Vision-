"""Rate limiting: ventana deslizante por IP y grupo de endpoint."""

from rate_limit import RateLimiter, client_ip, group_for_path


def test_allows_under_the_limit_and_blocks_over_it():
    limiter = RateLimiter(limits={"default": 2}, default=2)
    assert limiter.allow("1.1.1.1", "default", now=0.0) == (True, 0)
    assert limiter.allow("1.1.1.1", "default", now=1.0) == (True, 0)
    allowed, retry = limiter.allow("1.1.1.1", "default", now=2.0)
    assert allowed is False
    assert retry >= 1


def test_each_ip_has_its_own_bucket():
    limiter = RateLimiter(limits={"default": 1}, default=1)
    assert limiter.allow("1.1.1.1", "default", now=0.0)[0] is True
    assert limiter.allow("1.1.1.1", "default", now=1.0)[0] is False
    assert limiter.allow("2.2.2.2", "default", now=1.0)[0] is True


def test_each_group_has_its_own_bucket():
    limiter = RateLimiter(limits={"index": 1, "search": 1}, default=1)
    assert limiter.allow("1.1.1.1", "index", now=0.0)[0] is True
    assert limiter.allow("1.1.1.1", "search", now=0.0)[0] is True
    assert limiter.allow("1.1.1.1", "index", now=0.5)[0] is False


def test_the_window_opens_again_after_sixty_seconds():
    limiter = RateLimiter(limits={"default": 1}, default=1)
    assert limiter.allow("1.1.1.1", "default", now=0.0)[0] is True
    assert limiter.allow("1.1.1.1", "default", now=59.0)[0] is False
    assert limiter.allow("1.1.1.1", "default", now=61.0)[0] is True


def test_disabled_allows_everything():
    limiter = RateLimiter(limits={"default": 1}, default=1, enabled=False)
    for _ in range(100):
        assert limiter.allow("1.1.1.1", "default", now=0.0)[0] is True


def test_group_for_path_covers_the_hot_endpoints():
    assert group_for_path("/v1/index") == "index"
    assert group_for_path("/v1/search?q=x") == "search"
    assert group_for_path("/v1/gee38/events") == "gee38"
    assert group_for_path("/v1/epg/bulk") == "epg"
    assert group_for_path("/v1/diag") == "diag"
    assert group_for_path("/v1/config") == "config"
    assert group_for_path("/v1/section/movies") == "default"


def test_client_ip_prefers_the_last_forwarded_entry():
    assert client_ip("1.2.3.4", None, "10.0.0.1") == "1.2.3.4"
    assert client_ip("spoofed, 1.2.3.4", None, "10.0.0.1") == "1.2.3.4"
    assert client_ip(None, "1.2.3.4", "10.0.0.1") == "1.2.3.4"
    assert client_ip(None, None, "10.0.0.1") == "10.0.0.1"
    assert client_ip(None, None, None) == "unknown"
