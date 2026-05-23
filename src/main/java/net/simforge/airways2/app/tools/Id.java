package net.simforge.airways2.app.tools;

import net.simforge.airways2.tools.Tools;
import net.simforge.commons.legacy.misc.Settings;
import org.sqids.Sqids;

import java.util.List;

public class Id {
    private static final Sqids sqids = Sqids.builder()
                .alphabet(Settings.get("sqids.alphabet"))
                .minLength(5)
                .build();

    public static String encode(int id) {
        return sqids.encode(List.of((long) id));
    }

    public static int decode(String str) {
        return sqids.decode(str).get(0).intValue();
    }

    @SuppressWarnings("unused")
    public static String generateRandomAlphabet() {
        String defaultAlphabet = Sqids.Builder.DEFAULT_ALPHABET;

        char[] buf = defaultAlphabet.toCharArray();
        for (int i = 0; i < 100; i++) {
            int pos1 = Tools.random(0, buf.length-1);
            int pos2 = Tools.random(0, buf.length-1);

            char c = buf[pos1];
            buf[pos1] = buf[pos2];
            buf[pos2] = c;
        }

        return new String(buf);
    }
}
