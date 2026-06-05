package net.simforge.airways2.app.filters;

import net.simforge.airways2.app.beans.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
public class AuthFilter extends OncePerRequestFilter {

    @Autowired
    private UserService userService;

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
            || uri.startsWith("/flight-dashboard")
            || uri.startsWith("/sim")) {
            processUserToken(request, response, filterChain);
        } else if (uri.startsWith("/admin")) {
            processAdminToken(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void processUserToken(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws IOException, ServletException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            send401Response(response);
            return;
        }

        String token = header.substring(7);

        Optional<Integer> userId = userService.findUserIdByToken(token);
        if (userId.isEmpty()) {
            send401Response(response);
            return;
        }

        request.setAttribute("userId", userId.get());
        filterChain.doFilter(request, response);
    }

    private void processAdminToken(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws IOException, ServletException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            send401Response(response);
            return;
        }

        String token = header.substring(7);

        boolean correctToken = userService.isCorrectAdminToken(token);
        if (!correctToken) {
            send401Response(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void send401Response(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Missing or invalid Authorization header");
    }
}
