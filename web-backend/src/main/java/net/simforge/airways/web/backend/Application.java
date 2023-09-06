package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        new AirwaysApp.StartupAction().run();

        SpringApplication.run(Application.class, args);

//        new AirwaysApp.ShutdownAction().run();
    }
}
