package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonsTest {
    private World world;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
    }

    private Persons.Person pilot(final int userId) {
        return world.persons().create(Persons.Type.Pilot, Persons.State.Idle, userId,
                Persons.LocationType.Airport, 1234, 77, "AK", 42);
    }

    @Test
    public void test__new_world_has_no_persons() {
        assertEquals(0, world.persons().all().count());
        assertTrue(world.persons().byId(1).isEmpty());
        assertEquals(List.of(), world.persons().checkHeartbeatIndex());
    }

    @Test
    public void test__created_person_keeps_all_the_fields() {
        final Persons.Person person = pilot(5);

        final Persons.Person loaded = world.persons().byId(person.getId()).orElseThrow();
        assertEquals(Persons.Type.Pilot, loaded.getType());
        assertEquals(Persons.State.Idle, loaded.getState());
        assertEquals(0, loaded.getHeartbeatTime());
        assertEquals(5, loaded.getUserId());
        assertTrue(loaded.isPlayerCharacter());
        assertEquals(Persons.LocationType.Airport, loaded.getLocationType());
        assertEquals(1234, loaded.getLocationId());
        assertEquals(77, loaded.getOriginCityId());
        assertEquals("AK", loaded.getInitials());
        assertEquals(42, loaded.getAvatarId());
    }

    @Test
    public void test__person_without_user_is_npc() {
        assertFalse(pilot(0).isPlayerCharacter());
    }

    @Test
    public void test__setters_change_the_fields_independently() {
        final Persons.Person person = pilot(0);

        person.setState(Persons.State.Resting);
        person.setLocation(Persons.LocationType.Hotel, 65535);
        person.setInitials("M");
        person.setType(Persons.Type.CabinCrew);

        assertEquals(Persons.State.Resting, person.getState());
        assertEquals(Persons.LocationType.Hotel, person.getLocationType());
        assertEquals(65535, person.getLocationId());
        assertEquals("M", person.getInitials());
        assertEquals(Persons.Type.CabinCrew, person.getType());
        assertEquals(77, person.getOriginCityId());
        assertEquals(42, person.getAvatarId());
    }

    @Test
    public void test__initials_are_two_letters_at_most_and_may_be_empty() {
        final Persons.Person person = pilot(0);

        assertThrows(IllegalArgumentException.class, () -> person.setInitials("ABC"));
        person.setInitials(null);
        assertEquals("", person.getInitials());
        person.setInitials("");
        assertEquals("", person.getInitials());
    }

    @Test
    public void test__person_who_is_nowhere_has_no_location_id() {
        final Persons.Person person = pilot(0);

        person.setLocation(Persons.LocationType.None, 0);
        assertEquals(Persons.LocationType.None, person.getLocationType());
        assertThrows(IllegalArgumentException.class, () -> person.setLocation(Persons.LocationType.None, 5));
    }

    @Test
    public void test__16_bit_fields_are_checked() {
        final Persons.Person person = pilot(0);

        assertThrows(IllegalArgumentException.class, () -> person.setUserId(65536));
        assertThrows(IllegalArgumentException.class, () -> person.setUserId(-1));
        assertThrows(IllegalArgumentException.class, () -> person.setLocation(Persons.LocationType.City, 65536));
        assertThrows(IllegalArgumentException.class, () -> person.setOriginCityId(65536));
        assertThrows(IllegalArgumentException.class, () -> person.setAvatarId(65536));
    }

    @Test
    public void test__heartbeat_index_follows_the_heartbeat_of_persons() {
        final Persons.Person first = pilot(0);
        final Persons.Person second = pilot(0);

        assertTrue(world.persons().nextForHeartbeat(Integer.MAX_VALUE).isEmpty());

        second.setHeartbeatTime(200);
        first.setHeartbeatTime(300);

        assertTrue(world.persons().nextForHeartbeat(199).isEmpty());
        assertEquals(second.getId(), world.persons().nextForHeartbeat(200).orElseThrow().getId());
        assertEquals(second.getId(), world.persons().nextForHeartbeat(1000).orElseThrow().getId());

        second.setHeartbeatTime(0);
        assertEquals(first.getId(), world.persons().nextForHeartbeat(1000).orElseThrow().getId());
        assertEquals(List.of(), world.persons().checkHeartbeatIndex());
    }

    @Test
    public void test__deleted_person_is_gone() {
        final Persons.Person person = pilot(0);

        world.persons().deleteById(person.getId());

        assertTrue(world.persons().byId(person.getId()).isEmpty());
        assertEquals(0, world.persons().all().count());
    }

    @Test
    public void test__record_is_40_bytes_and_persons_survive_save_and_load(@TempDir final Path dir) throws IOException {
        final Persons.Person first = pilot(3);
        final Persons.Person second = pilot(0);
        second.setInitials("MB");

        world.persons().save(dir);

        // one byte of the record header and the data, 40 bytes all together (the record has grown from 32 bytes, the file is persons2)
        assertEquals(2 * 40, Files.size(dir.resolve("persons2")));

        final Persons loaded = new Persons();
        loaded.loadIfExists(dir);
        assertEquals(2, loaded.all().count());
        assertEquals(3, loaded.byId(first.getId()).orElseThrow().getUserId());
        assertEquals("AK", loaded.byId(first.getId()).orElseThrow().getInitials());
        assertEquals("MB", loaded.byId(second.getId()).orElseThrow().getInitials());
        assertEquals(Persons.LocationType.Airport, loaded.byId(second.getId()).orElseThrow().getLocationType());
    }

    // ---- the record is 40 bytes after the extension (the file is persons2); the new fields are not used yet, but they survive the save and the load

    @Test
    public void test__new_fields_survive_the_save_and_load(@TempDir final Path dir) throws IOException {
        pilot(3);
        world.persons().save(dir);
        // the record is 40 bytes: the header, the 31 bytes of the old fields, then the new ones: pilotJourneyId (3 bytes), the reserves (1 and 4 bytes)
        final byte[] bytes = Files.readAllBytes(dir.resolve("persons2"));
        assertEquals(40, bytes.length);
        bytes[32] = 0x01;
        bytes[33] = 0x02;
        bytes[34] = 0x03;
        Files.write(dir.resolve("persons2"), bytes);

        final Persons loaded = new Persons();
        loaded.loadIfExists(dir);
        loaded.byId(1).orElseThrow().setInitials("ZZ");
        loaded.save(dir);

        // the new bytes stay as they were, the old fields work as before
        final byte[] after = Files.readAllBytes(dir.resolve("persons2"));
        assertEquals(40, after.length);
        assertEquals(0x01, after[32]);
        assertEquals(0x02, after[33]);
        assertEquals(0x03, after[34]);
        assertEquals("ZZ", loaded.byId(1).orElseThrow().getInitials());
    }

    @Test
    public void test__heartbeat_index_is_built_by_the_load_of_the_file(@TempDir final Path dir) throws IOException {
        final Persons.Person first = pilot(3);
        first.setHeartbeatTime(1000);
        final Persons.Person second = pilot(0);
        second.setHeartbeatTime(500);
        final Persons.Person third = pilot(0);
        world.persons().deleteById(third.getId());
        world.persons().save(dir);

        final Persons loaded = new Persons();
        loaded.loadIfExists(dir);

        assertEquals(2, loaded.all().count());
        assertEquals(second.getId(), loaded.nextForHeartbeat(10_000).orElseThrow().getId());
        assertEquals(List.of(), loaded.checkHeartbeatIndex());
    }

    // ---- the initials are 12 bits of a 16 bit field, the highest bit is the warp drive

    @Test
    public void test__initials_survive_every_letter_and_digit() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "", 0);
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (final char first : alphabet.toCharArray()) {
            for (final char second : alphabet.toCharArray()) {
                person.setInitials("" + first + second);
                assertEquals("" + first + second, person.getInitials());
            }
            person.setInitials("" + first);
            assertEquals("" + first, person.getInitials());
        }
        person.setInitials("");
        assertEquals("", person.getInitials());
    }

    @Test
    public void test__a_letter_which_is_not_english_is_refused() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 0);
        assertThrows(IllegalArgumentException.class, () -> person.setInitials("\u0410B"));
        assertThrows(IllegalArgumentException.class, () -> person.setInitials("A B"));
        assertThrows(IllegalArgumentException.class, () -> person.setInitials(" A"));
        assertEquals("AK", person.getInitials());
    }

    @Test
    public void test__a_number_of_an_old_format_gives_empty_initials() {
        assertEquals("", Persons.decodeInitials(63 * 63));
        assertEquals("", Persons.decodeInitials(4095));
        assertEquals("", Persons.decodeInitials(-1));
    }

    @Test
    public void test__the_warp_drive_flag_does_not_touch_the_initials_and_the_other_way_round() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 106);
        assertFalse(person.isWarpDrive());

        person.setWarpDrive(true);
        assertTrue(person.isWarpDrive());
        assertEquals("AK", person.getInitials());

        person.setInitials("zZ");
        assertTrue(person.isWarpDrive());
        assertEquals("zZ", person.getInitials());

        person.setWarpDrive(false);
        assertFalse(person.isWarpDrive());
        assertEquals("zZ", person.getInitials());
        // the other fields of the record are where they were
        assertEquals(106, person.getAvatarId());
    }
}
