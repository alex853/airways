package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.pblc.CalendarKeys;
import net.simforge.airways2.app.userstorage.UserStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * The management of the calendar links under the admin token (docs/calendar.md): the secret of the server and the link of a user.
 */
@RestController
@RequestMapping("/admin/calendar")
@CrossOrigin
public class CalendarAdminController {
    private static final Logger log = LoggerFactory.getLogger(CalendarAdminController.class);

    /**
     * A new secret, saved to the disk. <b>All the calendar links given out before stop working</b>. The secret itself is never shown.
     */
    @PostMapping(value = "/regenerate-secret", produces = "text/plain")
    public String regenerateSecret() {
        try {
            CalendarKeys.getInstance().regenerate();
        } catch (final IOException e) {
            log.error("calendar - unable to save the secret", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "unable to save the secret: " + e.getMessage());
        }
        return "The secret is regenerated, all the old calendar links stopped to work";
    }

    /**
     * The link of a user: the path to give to the pilot (add the address of the server). 409 while there is no secret.
     */
    @GetMapping("/user/{id}/link")
    public LinkDto link(@PathVariable final int id) {
        if (UserStorage.getInstance().findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user #" + id + " not found");
        }
        final String slug = CalendarKeys.getInstance().slugOf(id);
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "there is no secret yet, regenerate it first");
        }
        return new LinkDto(id, "/calendar/" + slug + ".ics");
    }

    @Data
    @AllArgsConstructor
    public static class LinkDto {
        private int userId;
        private String path;
    }
}
