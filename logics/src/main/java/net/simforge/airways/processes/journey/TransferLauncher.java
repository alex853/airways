/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processes.journey.event.ArrivalToAirportFromCity;
import net.simforge.airways.processes.journey.event.CancelOnArrivalToCity;
import net.simforge.airways.processes.journey.event.TransferStarted;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class TransferLauncher {
    public static void scheduleTransferToAirport(ProcessEngine engine, Session session, Journey journey, Airport toAirport, LocalDateTime deadline) {
        BM.start("TransferLauncher.scheduleTransferToAirport");
        try {
            journey = session.load(Journey.class, journey.getId());

            double distance = calcMaxDistance(session, journey, toAirport.getCoords());
            int durationMinutes = calcDurationMinutes(distance);

            Transfer transfer = new Transfer();
            transfer.setToAirport(toAirport);
            transfer.setJourney(journey);
            transfer.setDistance(distance);
            transfer.setDuration(durationMinutes);
            transfer.setOnStartedStatus(Journey.Status.TransferToAirport);
            transfer.setOnFinishedEvent(ArrivalToAirportFromCity.class.getName());
            session.save(transfer);

            journey.setTransfer(transfer);
            session.update(journey);

            engine.scheduleEvent(session, TransferStarted.class, transfer, deadline.minusMinutes(durationMinutes));
        } finally {
            BM.stop();
        }
    }

    public static void startTransferToCityThenEvent(ProcessEngine engine, Session session, Journey journey, City toCity, Class<? extends Event> eventClass) {
        BM.start("TransferLauncher.startTransferToCityThenEvent");
        try {
            journey = session.load(Journey.class, journey.getId());

            double distance = calcMaxDistance(session, journey, toCity.getCoords());
            int durationMinutes = calcDurationMinutes(distance);

            Transfer transfer = new Transfer();
            transfer.setToCity(toCity);
            transfer.setJourney(journey);
            transfer.setDistance(distance);
            transfer.setDuration(durationMinutes);
            transfer.setOnStartedStatus(Journey.Status.TransferToCity);
            transfer.setOnFinishedEvent(eventClass.getName());
            session.save(transfer);

            journey.setTransfer(transfer);
            session.update(journey);

            engine.fireEvent(session, TransferStarted.class, transfer);
        } finally {
            BM.stop();
        }
    }

    public static void startTransferToBiggestCityThenCancel(ProcessEngine engine, Session session, Journey journey) {
        BM.start("TransferLauncher.startTransferToBiggestCityThenCancel");
        try {
            List<Person> persons = JourneyOps.getPersons(session, journey);
            List<Airport> airports = persons.stream().map(Person::getLocationAirport).collect(Collectors.toList());
            Airport currentAirport = airports.get(0);

            //noinspection unchecked
            List<City> cities = session
                    .createQuery("select ac.city " +
                            "from Airport2City ac " +
                            "where ac.airport = :airport")
                    .setEntity("airport", currentAirport)
                    .list();

            City theBiggestCity = null;
            for (City city : cities) {
                if (theBiggestCity == null) {
                    theBiggestCity = city;
                    continue;
                }

                if (theBiggestCity.getPopulation() < city.getPopulation()) {
                    theBiggestCity = city;
                }
            }

            //noinspection ConstantConditions
            session.save(EventLog.make(journey, "Transfer & Cancel to city " + theBiggestCity.getName()));

            TransferLauncher.startTransferToCityThenEvent(engine, session, journey, theBiggestCity, CancelOnArrivalToCity.class);

        } finally {
            BM.stop();
        }
    }

    private static int calcDurationMinutes(double distance) {
        return (int) ((distance / 25) * 60 + 15);
    }

    private static double calcMaxDistance(Session session, Journey journey, Geo.Coords toCoords) {
        BM.start("TransferLauncher.calcMaxDistance");
        try {

            List<Person> persons = JourneyOps.getPersons(session, journey);
            double maxDistance = persons.stream().mapToDouble(person -> {
                if (person.getLocationCity() != null)
                    return Geo.distance(person.getLocationCity().getCoords(), toCoords);
                if (person.getLocationAirport() != null)
                    return Geo.distance(person.getLocationAirport().getCoords(), toCoords);
                return 0.0;
            }).max().orElse(0.0);

            return maxDistance;
        } finally {
            BM.stop();
        }
    }
}
