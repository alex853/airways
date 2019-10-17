/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.model.*;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.flight.*;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.flow.City2CityFlowStats;
import net.simforge.airways.model.flow.CityFlow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.Airport2City;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.geo.Country;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.processengine.entities.TaskEntity;
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
            Itinerary.class,
            Transfer.class,
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
                .entities(new Class[]{TaskEntity.class})
                .build();
    }
}
