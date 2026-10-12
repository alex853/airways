package net.simforge.airways2.app.userstorage;

import java.util.Optional;

// todo ak2 temporary entity, it is not decided yet whether it will be 'user' or 'pilot'
public class User {
    private final int id;
    private final String token;
    private volatile String lastLocationAirport;

    public User(final int id, final String token, final String lastLocationAirport) {
        this.id = id;
        this.token = token;
        this.lastLocationAirport = lastLocationAirport;
    }

    public int getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    // ICAO of the airport where the user landed last time
    public Optional<String> getLastLocationAirport() {
        return Optional.ofNullable(lastLocationAirport);
    }

    void setLastLocationAirport(final String lastLocationAirport) {
        this.lastLocationAirport = lastLocationAirport;
    }
}
