package net.simforge.airways2.app.vatsimtracker;

import net.simforge.networkview.core.Position;

public class Flightplan {
    private final String filedAt;
    private final String aircraftType;
    private final String departure;
    private final String destination;

    public Flightplan(final String filedAt, final String aircraftType, final String departure, final String destination) {
        this.filedAt = filedAt;
        this.aircraftType = aircraftType;
        this.departure = departure;
        this.destination = destination;
    }

    public Flightplan(final Position position) {
        this(position.getAirportIcao(), position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
    }

    public String getFiledAt() {
        return filedAt;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public String getDeparture() {
        return departure;
    }

    public String getDestination() {
        return destination;
    }

    public boolean isValid() {
        return determineStatus() == Status.AllGood;
    }

    public Status getStatus() {
        return determineStatus();
    }

    public boolean isSame(final Flightplan flightplan) {
        return filedAt.equals(flightplan.filedAt)
                && aircraftType.equals(flightplan.aircraftType)
                && departure.equals(flightplan.departure)
                && destination.equals(flightplan.destination);
    }

    public boolean isValidDestinationLocation(final String airportIcao) {
        return destination.equals(airportIcao);
    }

    private Status determineStatus() {
        if (aircraftType == null) {
            return Status.FP_TypeUnknown;
        } else if (filedAt == null) {
            return Status.FP_NoPositionAirport;
        } else if (departure == null || destination == null) {
            return Status.FP_NoRoute;
        } else if (!departure.equals(filedAt)) {
            return Status.FP_DepWrong;
        } else if (!PilotContext.worldIcaos.contains(departure)) {
            return Status.FP_DepWrong;
        } else if (!PilotContext.worldIcaos.contains(destination)) {
            return Status.FP_DestOutWorld;
        } else {
            return Status.AllGood;
        }
    }

    public enum Status {
        AllGood,
        FP_TypeUnknown,
        FP_NotOnGround,
        FP_NotInAirport,
        FP_NoPositionAirport,
        FP_NoRoute,
        FP_DepWrong,
        FP_DestOutWorld
    }
}
