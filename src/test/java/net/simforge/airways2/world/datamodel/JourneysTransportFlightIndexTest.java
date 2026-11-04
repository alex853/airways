package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The journeys of a transport flight are found by the index kept by Journeys (by both of the flight fields of a journey), it follows the changes of the flights, the
 * deletion and the loading.
 */
public class JourneysTransportFlightIndexTest {
    private World world;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
    }

    private Journeys.Journey journey() {
        return world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 3, CabinLayout.Service.Y);
    }

    private List<Integer> anyOf(final Journeys journeys, final int transportFlightId) {
        return journeys.allByAnyTransportFlightId(transportFlightId).map(Journeys.Journey::getId).toList();
    }

    private List<Integer> firstOf(final int transportFlightId) {
        return world.journeys().allByTransportFlight1Id(transportFlightId).map(Journeys.Journey::getId).toList();
    }

    @Test
    public void journeysOfAFlightAreFoundInTheOrderOfIds() {
        final Journeys.Journey j1 = journey();
        final Journeys.Journey j2 = journey();
        final Journeys.Journey j3 = journey();
        j3.setTransportFlight1Id(7);
        j1.setTransportFlight1Id(7);
        j2.setTransportFlight1Id(8);

        assertEquals(List.of(j1.getId(), j3.getId()), firstOf(7));
        assertEquals(List.of(j2.getId()), firstOf(8));
        assertEquals(List.of(), firstOf(9));
    }

    @Test
    public void bothFieldsOfAJourneyAreIndexed() {
        final Journeys.Journey j = journey();
        j.setTransportFlight1Id(7);
        j.setTransportFlight2Id(8);

        assertEquals(List.of(j.getId()), anyOf(world.journeys(), 7));
        assertEquals(List.of(j.getId()), anyOf(world.journeys(), 8));
        // the first flight is the one of the field 1 only
        assertEquals(List.of(j.getId()), firstOf(7));
        assertEquals(List.of(), firstOf(8));
    }

    @Test
    public void changeOfAFlightMovesTheJourney() {
        final Journeys.Journey j = journey();
        j.setTransportFlight1Id(7);

        j.setTransportFlight1Id(8);
        assertEquals(List.of(), anyOf(world.journeys(), 7));
        assertEquals(List.of(j.getId()), anyOf(world.journeys(), 8));

        j.setTransportFlight1Id(0);
        assertEquals(List.of(), anyOf(world.journeys(), 8));
    }

    @Test
    public void theSameFlightInBothFieldsStaysUntilBothAreCleared() {
        final Journeys.Journey j = journey();
        j.setTransportFlight1Id(7);
        j.setTransportFlight2Id(7);

        j.setTransportFlight1Id(0);
        assertEquals(List.of(j.getId()), anyOf(world.journeys(), 7));

        j.setTransportFlight2Id(0);
        assertEquals(List.of(), anyOf(world.journeys(), 7));
    }

    @Test
    public void deletedJourneyIsNotFound() {
        final Journeys.Journey j1 = journey();
        final Journeys.Journey j2 = journey();
        j1.setTransportFlight1Id(7);
        j2.setTransportFlight1Id(7);
        j2.setTransportFlight2Id(8);

        world.journeys().deleteById(j2.getId());

        assertEquals(List.of(j1.getId()), anyOf(world.journeys(), 7));
        assertEquals(List.of(), anyOf(world.journeys(), 8));
    }

    @Test
    public void journeysMayBeChangedWhileTheJourneysOfTheirFlightAreWalked() {
        final List<Journeys.Journey> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Journeys.Journey j = journey();
            j.setTransportFlight1Id(7);
            all.add(j);
        }

        // the flight of every journey is cleared while they are walked (an unload of the flight does it)
        final List<Integer> walked = new ArrayList<>();
        world.journeys().allByTransportFlight1Id(7).forEach(j -> {
            walked.add(j.getId());
            j.setTransportFlight1Id(0);
        });

        assertEquals(all.stream().map(Journeys.Journey::getId).toList(), walked);
        assertEquals(List.of(), firstOf(7));
    }

    @Test
    public void indexIsRebuiltAfterLoading(@TempDir final Path dir) throws IOException {
        final Journeys.Journey j1 = journey();
        final Journeys.Journey j2 = journey();
        j1.setTransportFlight1Id(7);
        j2.setTransportFlight1Id(8);
        j2.setTransportFlight2Id(7);
        world.journeys().save(dir);

        final Journeys loaded = new Journeys();
        loaded.loadIfExists(dir);

        assertEquals(List.of(j1.getId(), j2.getId()), anyOf(loaded, 7));
        assertEquals(List.of(j2.getId()), anyOf(loaded, 8));
    }
}
