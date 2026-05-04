package net.simforge.airways2.world;

public class AircraftTypeRemapping {
    public static String remap(String aircraftType) {
        return switch (aircraftType) {
            case "A32N" -> "A20N";
            case "A330" -> "A333";
            case "A340" -> "A346";
            case "A350" -> "A35K";
            case "B747" -> "B748";
            case "B777" -> "B773";
            case "E175" -> "E75S";
            default -> aircraftType;
        };
    }
}
