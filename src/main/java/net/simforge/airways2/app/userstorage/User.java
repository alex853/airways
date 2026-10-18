package net.simforge.airways2.app.userstorage;

import java.util.Optional;

// todo ak2 temporary entity, it is not decided yet whether it will be 'user' or 'pilot'
public class User {
    private final int id;
    private final String token;
    private volatile String lastLocationAirport;
    private final int personId;
    private final int vatsimCid;

    public User(final int id, final String token, final String lastLocationAirport, final int personId, final int vatsimCid) {
        this.id = id;
        this.token = token;
        this.lastLocationAirport = lastLocationAirport;
        this.personId = personId;
        this.vatsimCid = vatsimCid;
    }

    public int getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    // the person (pilot) of the user in the world, zero if there is none
    public int getPersonId() {
        return personId;
    }

    // VATSIM CID of the user, zero if it is not known
    public int getVatsimCid() {
        return vatsimCid;
    }

    // ICAO of the airport where the user landed last time
    public Optional<String> getLastLocationAirport() {
        return Optional.ofNullable(lastLocationAirport);
    }

    void setLastLocationAirport(final String lastLocationAirport) {
        this.lastLocationAirport = lastLocationAirport;
    }
}
