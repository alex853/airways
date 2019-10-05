/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence;

import net.simforge.airways.persistence.model.*;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.*;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.City2CityFlowStats;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.Airport2City;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.journey.JourneyItinerary;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.SessionFactory;

public class Airways {

    public static final int ACTIVE_DATASET = 0;
    public static final int INACTIVE_DATASET = 1;
    public static final int TAGEO_COM_DATASET = 2;
    public static final int FSECONOMY_DATASET = 3;

    public static final Class[] entities = {
            Airport.class,
            Airport2City.class,
            Country.class,
            City.class,

            EventLogEntry.class,

            CityFlow.class,
            City2CityFlow.class,
            City2CityFlowStats.class,

            Journey.class,
            JourneyItinerary.class,
            Person.class,

            Pilot.class,

            AircraftType.class,
            Aircraft.class,

            Airline.class,

            TimetableRow.class,
            TransportFlight.class,

            Flight.class,
            PilotAssignment.class,
            AircraftAssignment.class,
    };

    public static SessionFactory buildSessionFactory() {
        return SessionFactoryBuilder
                .forDatabase("airways")
                .entities(entities)
                .build();
    }
}
