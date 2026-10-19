package net.simforge.airways2.pilottracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.simforge.airways2.pilottracker.track.TrackLeg;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stores sim tracker contexts of users in a JSON file, so they survive restarts of the application.
 * <p>
 * Not stored: user actions (they are recalculated from the world and the context on refresh) and the airports list.
 * The file is written atomically (temp file + move). A missing, unreadable or foreign-version file is not an error
 * for restoring, it just gives no contexts.
 */
public class SimTrackerContextStore {
    private static final Logger log = LoggerFactory.getLogger(SimTrackerContextStore.class);

    static final int VERSION = 1;

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public SimTrackerContextStore(final Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /**
     * Saves contexts which have a position, contexts without a position are skipped.
     *
     * @return count of saved contexts
     */
    public int save(final Collection<SimTracker.Context> contexts, final long nowMillis) throws IOException {
        final FileDto dto = new FileDto();
        dto.version = VERSION;
        dto.savedAt = nowMillis;
        dto.contexts = new ArrayList<>();
        for (final SimTracker.Context context : contexts) {
            if (context.getPosition() == null) {
                continue;
            }
            dto.contexts.add(toDto(context));
        }

        final Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(dto), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        return dto.contexts.size();
    }

    /**
     * Loads contexts, the ones with a position older than maxAgeMillis are dropped.
     * Never throws: any problem gives an empty result and a log line.
     */
    public Result load(final long nowMillis, final long maxAgeMillis) {
        if (!Files.exists(file)) {
            log.info("sim tracker contexts file {} does not exist, nothing to restore", file);
            return new Result(List.of(), 0, 0);
        }

        final FileDto dto;
        try {
            dto = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileDto.class);
        } catch (final Exception e) {
            log.error("unable to read sim tracker contexts file {}, nothing is restored", file, e);
            return new Result(List.of(), 0, 0);
        }

        if (dto == null || dto.contexts == null) {
            log.warn("sim tracker contexts file {} is empty, nothing is restored", file);
            return new Result(List.of(), 0, 0);
        }
        if (dto.version != VERSION) {
            log.warn("sim tracker contexts file {} has version {}, expected {}, nothing is restored", file, dto.version, VERSION);
            return new Result(List.of(), 0, 0);
        }

        final List<SimTracker.Context> restored = new ArrayList<>();
        int stale = 0;
        int broken = 0;
        for (final ContextDto contextDto : dto.contexts) {
            try {
                final SimTracker.Context context = fromDto(contextDto);
                if (nowMillis - context.getPosition().getTime() > maxAgeMillis) {
                    stale++;
                    continue;
                }
                restored.add(context);
            } catch (final Exception e) {
                broken++;
                log.warn("sim tracker context of user {} is broken, skipped", contextDto != null ? contextDto.userId : "?", e);
            }
        }

        return new Result(restored, stale, broken);
    }

    public record Result(List<SimTracker.Context> restored, int stale, int broken) {
    }

    private static ContextDto toDto(final SimTracker.Context context) {
        final TrackPosition position = context.getPosition();

        final ContextDto dto = new ContextDto();
        dto.userId = context.getUserId();
        dto.position = new PositionDto();
        dto.position.time = position.getTime();
        dto.position.onGround = position.isOnGround();
        dto.position.lat = position.getCoords().getLat();
        dto.position.lon = position.getCoords().getLon();
        dto.position.airportIcao = position.getAirportIcao();
        dto.position.heading = position.getHeading();
        dto.position.altitude = position.getAltitude();
        dto.parkingBrake = context.isParkingBrake();
        dto.numberOfEnginesRunning = context.getNumberOfEnginesRunning();
        dto.trackTrail = context.getTrackTrail() != null
                ? context.getTrackTrail().stream().map(TrackLeg::toString).toList()
                : null;
        dto.measuredGs = context.getMeasuredGs();
        dto.flightMissionId = context.getFlightMissionId();
        if (context.getNightMeter() != null) {
            dto.hasNightMeter = true;
            dto.nightSeconds = context.getNightMeter().getNightSeconds();
            dto.nightCountedAt = context.getNightMeter().getCountedAt();
        }
        return dto;
    }

    private static SimTracker.Context fromDto(final ContextDto dto) {
        if (dto == null || dto.position == null || dto.userId <= 0) {
            throw new IllegalArgumentException("context without user or position");
        }

        final TrackPosition position = new TrackPosition(
                dto.position.time,
                dto.position.onGround,
                Geo.coords(dto.position.lat, dto.position.lon),
                dto.position.airportIcao,
                dto.position.heading,
                dto.position.altitude);

        final List<TrackLeg> trackTrail = dto.trackTrail != null
                ? dto.trackTrail.stream().map(TrackLeg::fromString).toList()
                : null;

        return SimTracker.Context.builder()
                .userId(dto.userId)
                .position(position)
                .parkingBrake(dto.parkingBrake)
                .numberOfEnginesRunning(dto.numberOfEnginesRunning)
                .trackTrail(trackTrail != null ? new ArrayList<>(trackTrail) : null)
                .measuredGs(dto.measuredGs)
                .flightMissionId(dto.flightMissionId)
                .nightMeter(dto.hasNightMeter
                        ? new NightTimeMeter(dto.nightSeconds, dto.nightCountedAt)
                        : null)
                .build();
    }

    private static class FileDto {
        int version;
        long savedAt;
        List<ContextDto> contexts;
    }

    private static class ContextDto {
        int userId;
        PositionDto position;
        boolean parkingBrake;
        int numberOfEnginesRunning;
        List<String> trackTrail;
        float measuredGs;
        Integer flightMissionId;
        // the night time meter of the flight, an old file has none of it
        boolean hasNightMeter;
        double nightSeconds;
        int nightCountedAt;
    }

    private static class PositionDto {
        long time;
        boolean onGround;
        double lat;
        double lon;
        String airportIcao;
        int heading;
        int altitude;
    }
}
