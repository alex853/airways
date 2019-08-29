package net.simforge.airways.processes.transportflight.event;

/**
 * 'Scheduled' event is fired at the moment when TimetableRow creates new TransportFlight instance.
 *
 * The event does the following:
 * 1) configures CheckinStarted timer
 * 2) starts Allocation activity
 */
public class Scheduled {
}
