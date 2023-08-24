package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.ops.GeoOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processes.journey.activity.LookingForTickets;
import net.simforge.airways.processes.transfer.pilot.PilotTransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/pilot")
@CrossOrigin
public class PilotController {

    private static final Logger log = LoggerFactory.getLogger(PilotController.class);

    @GetMapping("/status")
    public PilotStatusDto getStatus() {
        final SessionInfo sessionInfo = SessionInfo.get();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {

            final Person person = session.get(Person.class, sessionInfo.getPersonId());
            final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

            if (!isSuitablePilot(person, pilot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
            }

            return new PilotStatusDto(
                    new PersonDto(person),
                    new PilotDto(pilot),
                    new PilotActionsDto(person, pilot)
            );
        }
    }

    @PostMapping("/travel/book")
    public void bookTravel(@RequestParam(value = "destinationCityId") final int destinationCityId) {
        final SessionInfo sessionInfo = SessionInfo.get();

        // todo rework it!
        RealTimeMachine timeMachine = new RealTimeMachine();
        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(AirwaysApp.getSessionFactory())
                .build();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            HibernateUtils.transaction(session, () -> {

                final Person person = session.get(Person.class, sessionInfo.getPersonId());
                final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

                if (!isSuitablePilot(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
                }

                if (!canTravel(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status of person or pilot");
                }

                final City originCity;
                if (person.getLocationCity() != null) {
                    originCity = person.getLocationCity();
                } else {
                    final Airport locationAirport = person.getLocationAirport();
                    originCity = GeoOps.loadBiggestCityLinkedToAirport(session, locationAirport);
                    if (originCity == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to find origin city");
                    }
                }

                final City destinationCity = session.get(City.class, destinationCityId);
                if (destinationCity == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to find destination city");
                }

                final City2CityFlow c2cFlow = (City2CityFlow) session
                        .createQuery("from City2CityFlow " +
                                "where fromFlow.city = :fromCity " +
                                "  and toFlow.city = :toCity")
                        .setParameter("fromCity", originCity)
                        .setParameter("toCity", destinationCity)
                        .uniqueResult();

                final Journey journey = new Journey();
                journey.setGroupSize(1);
                journey.setFromCity(originCity);
                journey.setToCity(destinationCity);
                journey.setC2cFlow(c2cFlow);
                journey.setStatus(Journey.Status.LookingForTickets);
                session.save(journey);
                EventLog.info(session, log, journey, String.format("PILOT TRAVEL - New journey from %s to %s", originCity.getName(), destinationCity.getName()), c2cFlow, originCity, destinationCity);

                engine.startActivity(session, LookingForTickets.class, journey, timeMachine.now().plusDays(1));

                person.setStatus(Person.Status.OnJourney);
                person.setJourney(journey);
                session.update(person);
                EventLog.info(session, log, person, String.format("PILOT TRAVEL - Decided to travel from %s to %s", originCity.getName(), destinationCity.getName()), journey);

                pilot.setStatus(Pilot.Status.Travelling);
                session.update(pilot);
                EventLog.info(session, log, pilot, String.format("PILOT TRAVEL - Decided to travel from %s to %s", originCity.getName(), destinationCity.getName()), journey);
            });
        }
    }

    @PostMapping("/transfer/to/airport")
    public void transferToAirport(@RequestParam(value = "destinationAirportId") final int destinationAirportId) {
        final SessionInfo sessionInfo = SessionInfo.get();

        // todo rework it!
        RealTimeMachine timeMachine = new RealTimeMachine();
        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(AirwaysApp.getSessionFactory())
                .build();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            HibernateUtils.transaction(session, () -> {

                final Person person = session.get(Person.class, sessionInfo.getPersonId());
                final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

                if (!isSuitablePilot(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
                }

                if (!canTransferToAirport(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status of person or pilot");
                }

                final City originCity = person.getLocationCity();

                final List<Airport> airports = GeoOps.loadAirportsLinkedToCity(session, originCity.getId());
                Optional<Airport> destinationAirport = airports.stream().filter(airport -> airport.getId() == destinationAirportId).findFirst();

                if (!destinationAirport.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid airport provided");
                }

                PilotTransferLauncher.transferToAirport(engine, session, person, destinationAirport.get());

            });
        }
    }

    @PostMapping("/transfer/to/city")
    public void transferToCity(@RequestParam(value = "destinationCityId") final int destinationCityId) {
        final SessionInfo sessionInfo = SessionInfo.get();

        // todo rework it!
        RealTimeMachine timeMachine = new RealTimeMachine();
        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(AirwaysApp.getSessionFactory())
                .build();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            HibernateUtils.transaction(session, () -> {

                final Person person = session.get(Person.class, sessionInfo.getPersonId());
                final Pilot pilot = session.get(Pilot.class, sessionInfo.getPilotId());

                if (!isSuitablePilot(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person or pilot loaded");
                }

                if (!canTransferToCity(person, pilot)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status of person or pilot");
                }

                final Airport originAirport = person.getLocationAirport();

                final List<City> airports = GeoOps.loadCitiesLinkedToAirport(session, originAirport.getId());
                Optional<City> destinationCity = airports.stream().filter(city -> city.getId() == destinationCityId).findFirst();

                if (!destinationCity.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid city provided");
                }

                PilotTransferLauncher.transferToCity(engine, session, person, destinationCity.get());

            });
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isSuitablePilot(Person person, Pilot pilot) {
        return person.getType() == Person.Type.Excluded
                && pilot.getType() == Pilot.Type.PlayerCharacter;
    }

    private static boolean canTravel(Person person, Pilot pilot) {
        return person.getStatus() == Person.Status.Idle
                && person.getJourney() == null
                && pilot.getStatus() == Pilot.Status.Idle;
    }

    private static boolean canTransferToCity(Person person, Pilot pilot) {
        return canTravel(person, pilot)
                && person.getLocationAirport() != null;
    }

    private static boolean canTransferToAirport(Person person, Pilot pilot) {
        return canTravel(person, pilot)
                && person.getLocationCity() != null;
    }

    private static class PilotStatusDto {
        private final PersonDto person;
        private final PilotDto pilot;
        private final PilotActionsDto actions;

        public PilotStatusDto(PersonDto person, PilotDto pilot, PilotActionsDto actions) {
            this.person = person;
            this.pilot = pilot;
            this.actions = actions;
        }

        public PersonDto getPerson() {
            return person;
        }

        public PilotDto getPilot() {
            return pilot;
        }

        public PilotActionsDto getActions() {
            return actions;
        }
    }

    private static class PersonDto {
        private final String fullName;
        private final String originCityName;
        private final String statusName;
        private final Integer locationCityId;
        private final String locationCityName;
        private final Integer locationAirportId;
        private final String locationAirportName;
        private final Integer journeyId;
        private final String journeyDesc;

        public PersonDto(Person person) {
            this.fullName = person.getName() + " " + person.getSurname();
            this.originCityName = person.getOriginCity().getCityWithCountryName();
            this.statusName = person.getStatus().toString();
            this.locationCityId = person.getLocationCity() != null ? person.getLocationCity().getId() : null;
            this.locationCityName = person.getLocationCity() != null ? person.getLocationCity().getCityWithCountryName() : null;
            this.locationAirportId = person.getLocationAirport() != null ? person.getLocationAirport().getId() : null;
            this.locationAirportName = person.getLocationAirport() != null ? person.getLocationAirport().getIcao() + " " + person.getLocationAirport().getName() : null;

            Journey journey = person.getJourney();
            if (journey != null) {
                this.journeyId = journey.getId();
                this.journeyDesc = "Travelling to "
                        + (journey.getToCity() != null ? journey.getToCity().getCityWithCountryName() : "'no city info'")
                        + ", " + journey.getStatus().toString();
            } else {
                this.journeyId = null;
                this.journeyDesc = null;
            }
        }

        public String getFullName() {
            return fullName;
        }

        public String getOriginCityName() {
            return originCityName;
        }

        public String getStatusName() {
            return statusName;
        }

        public Integer getLocationCityId() {
            return locationCityId;
        }

        public String getLocationCityName() {
            return locationCityName;
        }

        public Integer getLocationAirportId() {
            return locationAirportId;
        }

        public String getLocationAirportName() {
            return locationAirportName;
        }

        public Integer getJourneyId() {
            return journeyId;
        }

        public String getJourneyDesc() {
            return journeyDesc;
        }
    }

    private static class PilotDto {
        private final String statusName;

        public PilotDto(Pilot pilot) {
            statusName = pilot.getStatus().toString();
        }

        public String getStatusName() {
            return statusName;
        }
    }

    private static class PilotActionsDto {
        private final boolean canTravel;
        private final boolean canTransferToCity;
        private final boolean canTransferToAirport;

        public PilotActionsDto(Person person, Pilot pilot) {
            this.canTravel = canTravel(person, pilot);
            this.canTransferToCity = canTransferToCity(person, pilot);
            this.canTransferToAirport = canTransferToAirport(person, pilot);
        }

        public boolean isCanTravel() {
            return canTravel;
        }

        public boolean isCanTransferToCity() {
            return canTransferToCity;
        }

        public boolean isCanTransferToAirport() {
            return canTransferToAirport;
        }
    }
}
