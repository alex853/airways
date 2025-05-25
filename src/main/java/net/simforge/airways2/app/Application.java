package net.simforge.airways2.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// todo ak1 pilot? mission gets pilot_id, pilot has type - npc/pc
// todo ak1 pilot_id vs pilot_assignment?
// todo ak1 how to add pilot_id to flight-missions?
// todo ak2 migrate to simforge.net
@SpringBootApplication
public class Application {

    public static void main(final String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (args.length == 1) {
            runWorldBuilderStep(args[0]);
            return;
        }

        SpringApplication.run(Application.class, args);
    }

    private static void runWorldBuilderStep(final String stepName) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final String className = "net.simforge.airways2.worldbuilder." + stepName;
        final Class stepClass = Class.forName(className);
        final Method main = stepClass.getMethod("main", String[].class);
        main.invoke(null, new Object[] { new String[0] });
    }

}
