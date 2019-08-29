package net.simforge.airways.model.person;

public interface Pilot /*extends BaseHeartbeatEntity, EventLog.Loggable, Auditable*/ {

    Integer getStatus();

    void setStatus(Integer status);

    Person getPerson();

    void setPerson(Person person);

    class Status {
        public static final int Idle = 100;
        public static final int IdlePlanned = 101; // temporarily added status for stupid allocation needs
        public static final int OnDuty = 200;
    }

}
