package net.simforge.airways2.app.filters;

import net.simforge.airways2.app.tools.Timing;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class TimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try (Timing.Timer ignore = Timing.label("http-request " + request.getRequestURI())) {
            filterChain.doFilter(request, response);
        }
    }
}
