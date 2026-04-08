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

//    @Autowired
//    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/busy-birds")) {
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

        Long userId = findUserIdByToken(token);
        if (userId == null) {
            send401Response(response);
            return;
        }

        request.setAttribute("userId", userId);
        filterChain.doFilter(request, response);
    }

    private static Long findUserIdByToken(String token) {
        return "123".equals(token) ? 1L : null;
    }

    private static void send401Response(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Missing or invalid Authorization header");
    }
}
