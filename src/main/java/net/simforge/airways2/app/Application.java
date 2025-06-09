package net.simforge.airways2.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// todo ak2 aircraft types
//          A306
//          A32N -> A20N mapping
//          E170,E175,E190,E195 and E2
//          SU95
//          B777 -> B773 mapping
//          MD11
//          AT76 and related
//          B722
//          B732
//          MD82
@SpringBootApplication
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(final String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (args.length == 1) {
            runWorldBuilderStep(args[0]);
            return;
        }

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
