package dev.tobyscamera.folia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void grantsTobysCameraAdminPermissionToOperatorsByDefault() throws IOException {
        String descriptor = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(descriptor.matches("(?s).*tobyscamera\\.admin:\\R\\s+description:.*\\R\\s+default: op\\R.*"));
    }
}
