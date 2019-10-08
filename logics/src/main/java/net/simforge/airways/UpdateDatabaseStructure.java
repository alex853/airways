/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.SessionFactory;

public class UpdateDatabaseStructure {
    public static void main(String[] args) {
        SessionFactory sessionFactory = SessionFactoryBuilder
                .forDatabase("airways")
                .entities(Airways.entities)
                .entities(new Class[]{TaskEntity.class})
                .updateSchemaIfNeeded()
                .build();
        System.out.println("Session Factory has been built");
        sessionFactory.close();
    }
}
