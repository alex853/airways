package net.simforge.airways.persistence;

import net.simforge.airways.persistence.model.EventLogEntry;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;

public class EventLog {
    public interface Loggable {
        Integer getId();
        String getEventLogCode();
    }

    public static EventLogEntry saveLog(Session session, Loggable primaryObject, String msg, Loggable... secondaryObjects) {
        BM.start("EventLogOps.saveLog");
        try {
            EventLogEntry entry = make(primaryObject, msg, secondaryObjects);

            session.save(entry);

            return entry;
        } finally {
            BM.stop();
        }
    }

    public static EventLogEntry make(Loggable primaryObject, String msg, Loggable... secondaryObjects) {
        BM.start("EventLogOps.make");
        try {
            EventLogEntry entry = new EventLogEntry();

            entry.setDt(JavaTime.nowUtc());
            entry.setPrimaryId(getId(primaryObject));
            entry.setMsg(msg);

            if (secondaryObjects.length > 0) entry.setSecondaryId1(getId(secondaryObjects[0]));
            if (secondaryObjects.length > 1) entry.setSecondaryId2(getId(secondaryObjects[1]));
            if (secondaryObjects.length > 2) entry.setSecondaryId3(getId(secondaryObjects[2]));

            return entry;
        } finally {
            BM.stop();
        }
    }

    private static String getId(Loggable object) {
        BM.start("EventLogOps.getId");
        try {
            int id = object.getId();
            String objectType = object.getEventLogCode();
            return objectType + ":" + id;
        } catch (Exception e) {
            throw new RuntimeException("Unable to make id for object of class " + object.getClass(), e);
        } finally {
            BM.stop();
        }
    }
}
