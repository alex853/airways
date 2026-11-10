package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotTravel;
import net.simforge.airways2.world.computations.ShadowJetPricing;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.commons.misc.Geo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The estimate of a travel by airline (the journey of a pilot from a city to a city, docs/pilot-airline-travel.md): how many flights it is, how much the tickets cost and how long
 * it may take. NOTHING IS BOOKED and nothing is changed in the world, it is a calculation for the window of the travel.
 * <p>
 * The route is found by the same steps as the search of a journey (JourneyProcessor.lookingForTickets), but over the FREQUENCIES of the flights between the airports (the flights of
 * the last days, Airport2AirportDailyFlightStat) instead of the flights which are open right now: the pilot starts at the airports of its city and, one flight after another,
 * 1. takes a flight to a hub which is closer to the destination than the city where the pilot is now (of such flights the one which cuts the distance most, of equal ones the most
 * frequent; a flight right to the destination, if it is a hub, is the best of them), and then goes on from the city of that hub;
 * 2. or a direct flight to an airport of the destination city (the most frequent);
 * 3. or two flights with a stopover (the pair which has the rarer flight the most frequent).
 * That is the route of the search (estimate). variants() gives it and the other ways to get there which the same flights allow (the best direct flight and the best pair of flights with
 * a stopover, if they are not the same route), SORTED BY THE FREQUENCY of the route, not by the steps of the search: the most frequent first (the frequency of a route is the one of its
 * rarest flight, the route is as frequent as its bottleneck), at equal frequency the one with fewer flights, then the cheaper one. So a route through a hub with frequent flights is
 * before a rare direct flight, and the other way round.
 * The distance to the destination only gets smaller, so there are no circles (a city which has been visited ends the search). The price of a flight is the price of the economy
 * ticket of ShadowJet for one passenger (ShadowJetPricing). THE TIME is not known: the flights are those of living people and there is no timetable, so the estimate only says that
 * one flight is one to two days (LEG_MIN_DAYS, LEG_MAX_DAYS).
 */
public final class AirlineTravelEstimate {
    // the flights of the journey by airline: a long way has a few of them, the search gives up after that many
    public static final int MAX_LEGS = 6;
    // one flight of the journey takes one to two days (waiting for the flight and the flight itself)
    public static final int LEG_MIN_DAYS = 1;
    public static final int LEG_MAX_DAYS = 2;

    /**
     * One flight of the route: the airports, the distance, how often the airports are connected (the flights in the days of the flight stats) and the price of the ticket.
     */
    public record Leg(int fromAirportId, String fromIcao, int toAirportId, String toIcao, double distanceNm, int flightsPerWeek, int price) {
    }

    /**
     * The estimate: the flights, the price of all of them and the time as the days from-to; the problem tells why there is no route (null when there is one). The title is the
     * name of the route for the page: a direct flight, through a hub or with a stopover (null when there is no route).
     */
    public record Result(List<Leg> legs, long price, int minDays, int maxDays, String problem, String title) {
        public boolean found() {
            return problem == null;
        }
    }

    private final World world;
    // the connections of the airports: the airport of departure to the airport of arrival to the number of flights in the days of the stats, only the flights which took place
    private final Map<Integer, Map<Integer, Integer>> flights = new HashMap<>();

    /**
     * The frequencies of the flights are read once, so the estimate of several places is made with one object.
     */
    public AirlineTravelEstimate(final World world) {
        this.world = world;
        world.airport2airportDailyFlightStats().all().forEach(stat -> {
            final int count = stat.getTotalCount();
            if (count > 0) {
                flights.computeIfAbsent(stat.getFromAirportId(), id -> new HashMap<>()).merge(stat.getToAirportId(), count, Integer::sum);
            }
        });
    }

    public Result estimate(final int fromCityId, final int toCityId) {
        final Optional<Cities.City> fromCity = world.cities().byId(fromCityId);
        final Optional<Cities.City> toCity = world.cities().byId(toCityId);
        if (fromCity.isEmpty() || toCity.isEmpty()) {
            return failure("There is no such city");
        }
        if (fromCityId == toCityId) {
            return failure("The pilot is in " + toCity.get().getName() + " already");
        }
        final double distance = Geo.distance(fromCity.get().getCoords(), toCity.get().getCoords());
        if (distance < PilotTravel.AIRLINE_MIN_DISTANCE_NM) {
            return failure("Too close, from " + PilotTravel.AIRLINE_MIN_DISTANCE_NM + " miles");
        }
        final Geo.Coords target = toCity.get().getCoords();
        final Set<Integer> targetAirports = airportsOf(toCityId);
        if (targetAirports.isEmpty()) {
            return failure("There are no airports in " + toCity.get().getName());
        }

        final List<Leg> legs = new ArrayList<>();
        final Set<Integer> visitedCities = new HashSet<>();
        int currentCityId = fromCityId;
        boolean arrived = false;
        while (!arrived && legs.size() < MAX_LEGS) {
            final Cities.City current = world.cities().byId(currentCityId).orElse(null);
            if (current == null || !visitedCities.add(currentCityId)) {
                return failure("No route found to " + toCity.get().getName());
            }
            final Set<Integer> currentAirports = airportsOf(currentCityId);
            if (currentAirports.isEmpty()) {
                return failure("There are no airports in " + current.getName());
            }
            final double distanceNow = Geo.distance(current.getCoords(), target);

            // 1. a flight to a hub which brings the pilot closer to the destination
            final Leg hubLeg = bestHubLeg(currentAirports, target, distanceNow);
            if (hubLeg != null) {
                legs.add(hubLeg);
                if (targetAirports.contains(hubLeg.toAirportId())) {
                    arrived = true;
                } else {
                    currentCityId = cityOfAirport(hubLeg.toAirportId());
                    if (currentCityId == 0) {
                        return failure("No route found to " + toCity.get().getName());
                    }
                }
                continue;
            }

            // 2. a direct flight
            final Leg directLeg = bestDirectLeg(currentAirports, targetAirports);
            if (directLeg != null) {
                legs.add(directLeg);
                arrived = true;
                continue;
            }

            // 3. two flights with a stopover
            final List<Leg> stopover = bestStopover(currentAirports, targetAirports, Set.of());
            if (stopover != null) {
                legs.addAll(stopover);
                arrived = true;
                continue;
            }

            return failure("No flights from " + current.getName() + " towards " + toCity.get().getName() + " yet");
        }
        if (!arrived) {
            return failure("No route found to " + toCity.get().getName() + " in " + MAX_LEGS + " flights");
        }

        return route(legs);
    }

    /**
     * The route of the search (estimate) and the other routes the flights allow: the best direct flight and the best pair of flights with a stopover, if they are not the routes which
     * are in the list already, the most frequent first (see the class). If there is no route at all, the list is one result with the problem.
     */
    public List<Result> variants(final int fromCityId, final int toCityId) {
        final Result likely = estimate(fromCityId, toCityId);
        if (!likely.found()) {
            return List.of(likely);
        }
        final Set<Integer> fromAirports = airportsOf(fromCityId);
        final Set<Integer> toAirports = airportsOf(toCityId);
        final List<Result> variants = new ArrayList<>();
        variants.add(likely);

        final Leg direct = bestDirectLeg(fromAirports, toAirports);
        if (direct != null) {
            addIfNew(variants, route(List.of(direct)));
        }
        // the stopover is looked for among the airports which the routes of the list do not go through: the best pair is often the route through the hub which is first already
        final Set<Integer> usedStops = new HashSet<>();
        for (final Leg leg : likely.legs().subList(0, likely.legs().size() - 1)) {
            usedStops.add(leg.toAirportId());
        }
        final List<Leg> stopover = bestStopover(fromAirports, toAirports, usedStops);
        if (stopover != null) {
            addIfNew(variants, route(stopover));
        }
        variants.sort(Comparator.comparingInt(AirlineTravelEstimate::frequencyOf).reversed()
                .thenComparingInt((Result variant) -> variant.legs().size())
                .thenComparingLong(Result::price));
        return variants;
    }

    // how often the route can be flown: the flights a week of its rarest flight
    private static int frequencyOf(final Result route) {
        return route.legs().stream().mapToInt(Leg::flightsPerWeek).min().orElse(0);
    }

    // a route is the same if its flights go between the same airports in the same order
    private static void addIfNew(final List<Result> variants, final Result candidate) {
        final List<String> key = airportsOf(candidate);
        if (variants.stream().noneMatch(variant -> airportsOf(variant).equals(key))) {
            variants.add(candidate);
        }
    }

    private static List<String> airportsOf(final Result result) {
        final List<String> airports = new ArrayList<>();
        for (final Leg leg : result.legs()) {
            if (airports.isEmpty()) {
                airports.add(leg.fromIcao());
            }
            airports.add(leg.toIcao());
        }
        return airports;
    }

    private Result route(final List<Leg> legs) {
        final long price = legs.stream().mapToLong(Leg::price).sum();
        return new Result(List.copyOf(legs), price, legs.size() * LEG_MIN_DAYS, legs.size() * LEG_MAX_DAYS, null, titleOf(legs));
    }

    // the name of the route: a direct flight, through the hubs, or with a stopover (the airports in the middle are named)
    private String titleOf(final List<Leg> legs) {
        if (legs.size() == 1) {
            return "Direct flight";
        }
        final List<String> middle = new ArrayList<>();
        boolean allHubs = true;
        for (int i = 0; i < legs.size() - 1; i++) {
            middle.add(legs.get(i).toIcao());
            allHubs &= world.airports().byId(legs.get(i).toAirportId()).map(Airports.Airport::isHub).orElse(false);
        }
        return (allHubs ? "Through " : "Stopover in ") + String.join(", ", middle);
    }

    private static Result failure(final String problem) {
        return new Result(List.of(), 0, 0, 0, problem, null);
    }

    // the airports which serve the city, without the airports which are out of the game
    private Set<Integer> airportsOf(final int cityId) {
        final Set<Integer> airports = new HashSet<>();
        world.airport2city().allByCityId(cityId)
                .map(Airport2City.Link::getAirportId)
                .forEach(airportId -> world.airports().byId(airportId).filter(a -> !a.isExcluded()).ifPresent(a -> airports.add(airportId)));
        return airports;
    }

    // the city where the pilot is after the landing at the airport: the biggest city which the airport serves (a journey takes a random one of them, the biggest is the estimate)
    private int cityOfAirport(final int airportId) {
        return world.airport2city().allByAirportId(airportId)
                .map(link -> world.cities().byId(link.getCityId()))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(Cities.City::getPopulation))
                .map(Cities.City::getId)
                .orElse(0);
    }

    private Leg bestHubLeg(final Set<Integer> fromAirports, final Geo.Coords target, final double distanceNow) {
        Leg best = null;
        double bestReduction = 0;
        for (final int fromAirportId : fromAirports) {
            for (final Map.Entry<Integer, Integer> connection : flights.getOrDefault(fromAirportId, Map.of()).entrySet()) {
                final Airports.Airport hub = world.airports().byId(connection.getKey()).orElse(null);
                if (hub == null || hub.isExcluded() || !hub.isHub()) {
                    continue;
                }
                final double reduction = distanceNow - Geo.distance(hub.getCoords(), target);
                if (reduction <= 0) {
                    continue;
                }
                if (best == null || reduction > bestReduction || (reduction == bestReduction && connection.getValue() > best.flightsPerWeek())) {
                    best = leg(fromAirportId, hub.getId(), connection.getValue());
                    bestReduction = reduction;
                }
            }
        }
        return best;
    }

    private Leg bestDirectLeg(final Set<Integer> fromAirports, final Set<Integer> toAirports) {
        Leg best = null;
        for (final int fromAirportId : fromAirports) {
            for (final Map.Entry<Integer, Integer> connection : flights.getOrDefault(fromAirportId, Map.of()).entrySet()) {
                if (toAirports.contains(connection.getKey()) && (best == null || connection.getValue() > best.flightsPerWeek())) {
                    best = leg(fromAirportId, connection.getKey(), connection.getValue());
                }
            }
        }
        return best;
    }

    private List<Leg> bestStopover(final Set<Integer> fromAirports, final Set<Integer> toAirports, final Set<Integer> excludedStops) {
        List<Leg> best = null;
        int bestRarer = 0;
        for (final int fromAirportId : fromAirports) {
            for (final Map.Entry<Integer, Integer> first : flights.getOrDefault(fromAirportId, Map.of()).entrySet()) {
                final Airports.Airport stopover = world.airports().byId(first.getKey()).orElse(null);
                if (stopover == null || stopover.isExcluded() || excludedStops.contains(first.getKey())) {
                    continue;
                }
                for (final Map.Entry<Integer, Integer> second : flights.getOrDefault(first.getKey(), Map.of()).entrySet()) {
                    final int rarer = Math.min(first.getValue(), second.getValue());
                    if (toAirports.contains(second.getKey()) && rarer > bestRarer) {
                        best = List.of(leg(fromAirportId, first.getKey(), first.getValue()), leg(first.getKey(), second.getKey(), second.getValue()));
                        bestRarer = rarer;
                    }
                }
            }
        }
        return best;
    }

    private Leg leg(final int fromAirportId, final int toAirportId, final int flightsPerWeek) {
        final Airports.Airport from = world.airports().byId(fromAirportId).orElseThrow();
        final Airports.Airport to = world.airports().byId(toAirportId).orElseThrow();
        final double distance = Geo.distance(from.getCoords(), to.getCoords());
        return new Leg(fromAirportId, from.getIcao(), toAirportId, to.getIcao(), distance, flightsPerWeek, ShadowJetPricing.price(distance, 1, CabinLayout.Service.Y));
    }
}
