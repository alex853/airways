package net.simforge.airways2.world;

public class AircraftTypeRemapping {
    public static String remap(String aircraftType) {
        return switch (aircraftType) {
            case "A32N" -> "A20N";
            case "B777" -> "B773";
            default -> aircraftType;
        };
    }
}
