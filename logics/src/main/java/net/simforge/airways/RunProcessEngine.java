/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.commons.legacy.BM;
import org.hibernate.SessionFactory;

public class RunProcessEngine {
    public static void main(String[] args) {
        new AirwaysApp.StartupAction().run();

        SessionFactory sessionFactory = AirwaysApp.getSessionFactory();

        ProcessEngine engine = ProcessEngineBuilder.create()
                .withTimeMachine(new RealTimeMachine())
                .withSessionFactory(sessionFactory)
                .build();

        BM.init("RunProcessEngine");

        engine.run();
    }
}
