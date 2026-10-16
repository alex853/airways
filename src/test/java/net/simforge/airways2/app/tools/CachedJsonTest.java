package net.simforge.airways2.app.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Item(String name, int value) {
    }

    private CachedJson cachedJson() {
        return CachedJson.of(objectMapper, List.of(new Item("alpha", 1), new Item("бета", 2)));
    }

    private static byte[] gunzip(final byte[] data) throws IOException {
        try (final GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    @Test
    public void test__json_is_the_same_as_the_object_mapper_gives() throws IOException {
        final List<Item> items = List.of(new Item("alpha", 1), new Item("бета", 2));

        final CachedJson cached = CachedJson.of(objectMapper, items);

        assertArrayEquals(objectMapper.writeValueAsBytes(items), cached.getJson());
    }

    @Test
    public void test__gzip_contains_the_same_json() throws IOException {
        final CachedJson cached = cachedJson();

        assertArrayEquals(cached.getJson(), gunzip(cached.getGzip()));
    }

    @Test
    public void test__client_without_accept_encoding_gets_plain_json() {
        final CachedJson cached = cachedJson();

        final ResponseEntity<byte[]> response = cached.toResponse(null);

        assertArrayEquals(cached.getJson(), response.getBody());
        assertNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals(cached.getJson().length, response.getHeaders().getContentLength());
        assertEquals("Accept-Encoding", response.getHeaders().getFirst(HttpHeaders.VARY));
    }

    @Test
    public void test__client_which_accepts_gzip_gets_the_compressed_json() throws IOException {
        final CachedJson cached = cachedJson();

        final ResponseEntity<byte[]> response = cached.toResponse("gzip, deflate, br");

        assertArrayEquals(cached.getGzip(), response.getBody());
        assertEquals("gzip", response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals(cached.getGzip().length, response.getHeaders().getContentLength());
        assertEquals("Accept-Encoding", response.getHeaders().getFirst(HttpHeaders.VARY));
        assertArrayEquals(cached.getJson(), gunzip(response.getBody()));
    }

    @Test
    public void test__the_same_arrays_are_sent_every_time() {
        final CachedJson cached = cachedJson();

        assertTrue(cached.toResponse("gzip").getBody() == cached.toResponse("gzip").getBody());
        assertTrue(cached.toResponse(null).getBody() == cached.toResponse("identity").getBody());
    }

    @Test
    public void test__accept_encoding_parsing() {
        assertTrue(CachedJson.acceptsGzip("gzip"));
        assertTrue(CachedJson.acceptsGzip("GZIP"));
        assertTrue(CachedJson.acceptsGzip("deflate, gzip;q=0.5"));
        assertTrue(CachedJson.acceptsGzip("gzip;q=1.0, identity; q=0.5, *;q=0"));
        assertFalse(CachedJson.acceptsGzip("gzip;q=0"));
        assertFalse(CachedJson.acceptsGzip("gzip; q=0.0"));
        assertFalse(CachedJson.acceptsGzip("identity"));
        assertFalse(CachedJson.acceptsGzip("deflate, br"));
        assertFalse(CachedJson.acceptsGzip(""));
        assertFalse(CachedJson.acceptsGzip(null));
        assertFalse(CachedJson.acceptsGzip("gzip;q=abc"));
    }

    @Test
    public void test__gzip_is_smaller_for_a_big_json() {
        final List<Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            items.add(new Item("airport number " + i, i));
        }

        final CachedJson cached = CachedJson.of(objectMapper, items);

        assertTrue(cached.getGzip().length < cached.getJson().length / 3);
    }
}
