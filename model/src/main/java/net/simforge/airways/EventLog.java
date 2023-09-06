package net.simforge.airways;

import net.simforge.airways.model.EventLogEntry;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.slf4j.Logger;

public class EventLog {

    public interface Loggable {
        Integer getId();
        String getEventLogCode();
    }

    public static EventLogEntry info(Session session, Logger log, Loggable primaryObject, String msg, Loggable... secondaryObjects) {
        BM.start("EventLog.info");
        try {
            EventLogEntry entry = _make(primaryObject, msg, secondaryObjects);
            session.save(entry);
            log.info("{} - {}", primaryObject, msg);
            return entry;
        } finally {
            BM.stop();
        }
    }

    public static EventLogEntry warn(Session session, Logger log, Loggable primaryObject, String msg, Loggable... secondaryObjects) {
        BM.start("EventLog.warn");
        try {
            EventLogEntry entry = _make(primaryObject, msg, secondaryObjects);
            session.save(entry);
            log.warn("{} - {}", primaryObject, msg);
            return entry;
        } finally {
            BM.stop();
        }
    }

    private static EventLogEntry _make(Loggable primaryObject, String msg, Loggable... secondaryObjects) {
        BM.start("EventLog._make");
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
        BM.start("EventLog.getId");
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
