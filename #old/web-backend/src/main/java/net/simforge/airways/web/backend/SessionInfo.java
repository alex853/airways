package net.simforge.airways.web.backend;

public class SessionInfo {
    private int vatsimPilotNumber = 913904;
    private int personId = 7;
    private int pilotId = 7;

    public int getVatsimPilotNumber() {
        return vatsimPilotNumber;
    }

    public int getPersonId() {
        return personId;
    }

    public int getPilotId() {
        return pilotId;
    }

    public static SessionInfo get() {
        return new SessionInfo();
    }
}
