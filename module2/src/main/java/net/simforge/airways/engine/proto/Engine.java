package net.simforge.airways.engine.proto;

import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.BaseEntity;
import org.hibernate.SessionFactory;

import java.time.LocalDateTime;

public class Engine {
    private TimeMachine timeMachine;

    public Engine(TimeMachine timeMachine, SessionFactory sessionFactory) {
        this.timeMachine = timeMachine;
    }

    public void startActivity(Class<? extends Activity> activityClass, BaseEntity entity) {

    }

    public void tick() {

    }

    public ActivityStatus getActivityStatus(Class<ScheduleFlight> activityClass, BaseEntity entity) {
        return null;
    }

    private class ActivityInfo {
        private Activity activity;
        private LocalDateTime lastActDt;
        private LocalDateTime nextActDt;
        private Object status;
    }

}
