package net.simforge.airways2.app.beans;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserService {

    public Optional<Integer> findUserIdByToken(String token) {
        return "123".equals(token) ? Optional.of(1) : Optional.empty();
    }

}
