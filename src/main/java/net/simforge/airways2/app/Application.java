package net.simforge.airways2.app;

import net.simforge.airways2.worldbuilder.World25_009_create_some_airports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// todo ak0 fs-integration imitation mode
//   step 1 - I declare that I am at some airport, at some aircraft, and I have intention to fly to another airport
//            the backend prepares flight, aircraft, transport flight, everything in Scheduled state, etc
//   step 2 - I start the flight, then I go through all the stages, including transport flight actions (new!)

// todo ak2 othh missing
// todo ak2 eddt existing
// todo ak2 check null airport cases
// todo ak2 go through aircraft types
// todo ak2 aircraft types with errors
//          A32N -> A20N mapping
//          B777 -> B773 mapping
// todo ak1 C700 - no aircraft data!
// todo ak1 AT76 - no aircraft data!
// todo ak3 minimize time between downloading a report and its processing
@SpringBootApplication
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(final String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        if (args.length == 1) {
            runWorldBuilderStep(args[0]);
            return;
        }

        World25_009_create_some_airports.main(args);

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
