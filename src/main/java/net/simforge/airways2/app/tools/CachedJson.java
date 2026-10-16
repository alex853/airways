package net.simforge.airways2.app.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A JSON response which is serialized and compressed once and then sent as it is. The compressed form is sent to the clients
 * which accept it ({@code Accept-Encoding: gzip}).
 */
public final class CachedJson {
    private final byte[] json;
    private final byte[] gzip;

    private CachedJson(final byte[] json, final byte[] gzip) {
        this.json = json;
        this.gzip = gzip;
    }

    public static CachedJson of(final ObjectMapper objectMapper, final Object value) {
        checkNotNull(objectMapper);

        try {
            final byte[] json = objectMapper.writeValueAsBytes(value);
            return new CachedJson(json, gzip(json));
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize the response to JSON", e);
        }
    }

    public byte[] getJson() {
        return json;
    }

    public byte[] getGzip() {
        return gzip;
    }

    /**
     * @param acceptEncoding the value of the Accept-Encoding header of the request, may be null
     */
    public ResponseEntity<byte[]> toResponse(final String acceptEncoding) {
        final boolean compressed = acceptsGzip(acceptEncoding);
        final byte[] body = compressed ? gzip : json;

        final ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING)
                .contentLength(body.length);
        if (compressed) {
            response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
        return response.body(body);
    }

    /**
     * True if the Accept-Encoding header allows gzip: 'gzip' is listed and its weight is not zero ('gzip;q=0' forbids it).
     */
    static boolean acceptsGzip(final String acceptEncoding) {
        if (acceptEncoding == null) {
            return false;
        }

        for (final String coding : acceptEncoding.split(",")) {
            final String[] parts = coding.trim().split(";");
            if (!"gzip".equals(parts[0].trim().toLowerCase(Locale.ROOT))) {
                continue;
            }

            for (int i = 1; i < parts.length; i++) {
                final String parameter = parts[i].trim().toLowerCase(Locale.ROOT);
                if (parameter.startsWith("q=")) {
                    try {
                        return Double.parseDouble(parameter.substring(2)) > 0;
                    } catch (final NumberFormatException e) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] gzip(final byte[] data) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, data.length / 4));
        try (final GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(data);
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to compress the response", e);
        }
        return bytes.toByteArray();
    }
}
