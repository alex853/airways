package net.simforge.airways2.app;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/busy-birds")
            || uri.startsWith("/flight-dashboard")) {
            processToken(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static void processToken(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws IOException, ServletException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            send401Response(response);
            return;
        }

        String token = header.substring(7);

        Integer userId = findUserIdByToken(token);
        if (userId == null) {
            send401Response(response);
            return;
        }

        request.setAttribute("userId", userId);
        filterChain.doFilter(request, response);
    }

    private static Integer findUserIdByToken(String token) {
        return "123".equals(token) ? 1 : null;
    }

    private static void send401Response(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Missing or invalid Authorization header");
    }
}
