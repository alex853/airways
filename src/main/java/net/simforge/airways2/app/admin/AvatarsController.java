package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.tools.AvatarList;
import net.simforge.airways2.tools.Avatars;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * The list of the pictures under the admin token (docs/avatars.md): the whole list with the four flags of every picture, and a change of one flag. Every change rewrites
 * the file of the list at once.
 */
@RestController
@RequestMapping("/admin/avatars")
@CrossOrigin
public class AvatarsController {

    @GetMapping
    public List<AvatarDto> getAvatars() {
        return list(Avatars.shared());
    }

    /**
     * @param file the name of the picture exactly as in the list (user_pilot.png)
     * @param flag forbidden, common, pilot or special
     */
    @PostMapping("/flag")
    public AvatarDto setFlag(@RequestParam("file") final String file,
                             @RequestParam("flag") final String flag,
                             @RequestParam("value") final boolean value) {
        return setFlag(Avatars.shared(), file, flag, value);
    }

    static List<AvatarDto> list(final AvatarList avatars) {
        return avatars.all().stream().map(AvatarDto::of).toList();
    }

    static AvatarDto setFlag(final AvatarList avatars, final String file, final String flag, final boolean value) {
        final AvatarList.Flag parsed;
        try {
            parsed = AvatarList.Flag.valueOf(flag);
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown flag '" + flag + "', expected forbidden, common, pilot or special");
        }
        try {
            return AvatarDto.of(avatars.setFlag(file, parsed, value));
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (final UncheckedIOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "the list of the avatars cannot be written");
        }
    }

    @Data
    @AllArgsConstructor
    public static class AvatarDto {
        private int id;
        private String file;
        private boolean forbidden;
        private boolean common;
        private boolean pilot;
        private boolean special;

        static AvatarDto of(final AvatarList.Avatar avatar) {
            return new AvatarDto(avatar.id(), avatar.file(), avatar.forbidden(), avatar.common(), avatar.pilot(), avatar.special());
        }
    }
}
