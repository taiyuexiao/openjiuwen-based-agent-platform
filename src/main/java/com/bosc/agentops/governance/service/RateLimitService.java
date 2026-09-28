package com.bosc.agentops.governance.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存滑动窗口限流（单实例语义；多实例部署需替换为 Redis 等外置实现，列入待办）。
 * 以 CallerPolicy id 为限流维度。
 */
@Component
public class RateLimitService {

    private static final long WINDOW_MS = 60_000;

    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** 允许调用则记录本次时间戳并返回 true；超限返回 false */
    public synchronized boolean tryAcquire(Long policyId, int limitPerMin) {
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(policyId, k -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
            window.pollFirst();
        }
        if (window.size() >= limitPerMin) {
            return false;
        }
        window.addLast(now);
        return true;
    }
}
