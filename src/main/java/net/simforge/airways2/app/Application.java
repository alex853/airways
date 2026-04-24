package net.simforge.airways2.app;

import net.simforge.airways2.worldbuilder.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// todo ak2 othh missing
// todo ak2 check null airport cases
// todo ak3 minimize time between downloading a report and its processing
@SpringBootApplication
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(final String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        if (args.length == 1) {
            runWorldBuilderStep(args[0]);
            return;
        }

//        World25_003_autonomia_airways.main(args);
//        World25_005_create_aircraft_types.main(args);
        World25_008_create_aircraft_types.main(args);
        World25_009_create_some_airports.main(args);
//        World25_011_busybirds.main(args);
        World25_012_selected_cities.main(args);
//        World25_099_f1_tour.main(args);

        SpringApplication.run(Application.class, args);
    }

    private static void runWorldBuilderStep(final String stepName) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        log.warn("world builder step - {}", stepName);
        final String className = "net.simforge.airways2.worldbuilder." + stepName;
        final Class stepClass = Class.forName(className);
        final Method main = stepClass.getMethod("main", String[].class);
        main.invoke(null, new Object[] { new String[0] });
        log.warn("world builder step - DONE");
    }
}
