/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.persistence.Airways;
import net.simforge.commons.hibernate.SessionFactoryBuilder;

import java.io.IOException;
import java.sql.SQLException;

public class BuildWorld {
    public static void main(String[] args) throws IOException, SQLException {
        SessionFactoryBuilder
                .forDatabase("airways")
                .entities(Airways.entities)
                .createSchemaIfNeeded()
                .build();

        TageoCom.main(args);
        ImportFSEconomyAirports.main(args);

        ActivateCities.main(args);
        ActivateAirports.main(args);

        UpdateAirport2CityLinks.main(args);

        UpdateCityFlows.main(args);
    }
}
