package me.fulcanelly.trust.me.bro.database;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class SqlResources {

    private SqlResources() {
    }

    public static String load(String path) {
        try (var resource = SqlResources.class.getResourceAsStream("/sql/" + path + ".sql")) {
            if (resource == null) {
                throw new IllegalStateException("Missing SQL resource: " + path);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read SQL resource: " + path, exception);
        }
    }
}
