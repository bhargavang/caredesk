package com.bhargavang.caredesk.common;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Small fixed-window counter, one per key (for example "login:203.0.113.7").
 * It lives in memory, which is fine for one or a few instances. For a large
 * cluster, swap it for a shared store such as Redis so limits are global.
 */
@Component
public class RateLimiter {

	private static final int MAX_KEYS = 50_000;
	private static final long STALE_AFTER_MILLIS = Duration.ofHours(2).toMillis();

	private record Window(long startMillis, AtomicInteger count) {
	}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	/**
	 * Counts one hit. Returns 0 when it is within the limit, otherwise the seconds
	 * until the window resets.
	 */
	public long tryAcquire(String key, int limit, Duration window) {
		long now = System.currentTimeMillis();
		Window w = current(key, window, now);
		if (w.count().incrementAndGet() <= limit) {
			return 0;
		}
		return Math.max(1, (w.startMillis() + window.toMillis() - now) / 1000);
	}

	public boolean isOverLimit(String key, int limit, Duration window) {
		Window w = windows.get(key);
		return w != null && System.currentTimeMillis() - w.startMillis() < window.toMillis() && w.count().get() >= limit;
	}

	public void record(String key, Duration window) {
		current(key, window, System.currentTimeMillis()).count().incrementAndGet();
	}

	public void reset(String key) {
		windows.remove(key);
	}

	/** Forgets every counter. Used by tests. */
	public void clear() {
		windows.clear();
	}

	private Window current(String key, Duration window, long now) {
		if (windows.size() > MAX_KEYS) {
			windows.entrySet().removeIf(e -> now - e.getValue().startMillis() > STALE_AFTER_MILLIS);
		}
		return windows.compute(key, (k, w) -> w == null || now - w.startMillis() >= window.toMillis()
				? new Window(now, new AtomicInteger())
				: w);
	}
}
