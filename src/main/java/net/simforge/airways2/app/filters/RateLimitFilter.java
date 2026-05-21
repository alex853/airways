package net.simforge.airways2.app.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int LIMIT = 10;

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong currentSecond = new AtomicLong(System.currentTimeMillis() / 1000);
    private final AtomicInteger droppedRequestCount = new AtomicInteger(0);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        long nowSecond = System.currentTimeMillis() / 1000;

        if (nowSecond != currentSecond.get()) {
            currentSecond.set(nowSecond);
            requestCount.set(0);
            if (droppedRequestCount.get() > 0) {
                log.warn("dropped {} requests in the last second", droppedRequestCount.get());
            }
            droppedRequestCount.set(0);
        }

        int count = requestCount.incrementAndGet();

        if (count > LIMIT) {
            response.setStatus(429);
            response.getWriter().write("Too Many Requests");
            droppedRequestCount.incrementAndGet();
            return;
        }

        filterChain.doFilter(request, response);
    }
}
