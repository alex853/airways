package net.simforge.airways2.app.beans;

import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.commons.legacy.misc.Settings;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserService {

    public Optional<Integer> findUserIdByToken(String token) {
        return UserStorage.getInstance().findByToken(token).map(User::getId);
    }

    public boolean isCorrectAdminToken(String token) {
        return Settings.get("admin.token").equals(token);
    }
}
