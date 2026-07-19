package dev.tobyscamera.common.protocol;

import java.util.Objects;

public record PhotoPresentation(String name, String description, boolean publicAddress, boolean publicPhotographer) {
    public static final PhotoPresentation DEFAULT = new PhotoPresentation("", "", true, true);

    public PhotoPresentation {
        name = Objects.requireNonNull(name, "name").trim();
        description = Objects.requireNonNull(description, "description").trim();
    }
}
