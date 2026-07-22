package me.fulcanelly.trust.me.bro.database.repository.coreprotect;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.List;

import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public final class CoreProtectDatabaseResolver {

    private final Plugin coreProtect;

    public File resolve() {
        if (coreProtect == null) {
            return null;
        }

        return List.of("database.db", "database.sqlite", "database.sqlite3")
                .stream()
                .map(name -> new File(coreProtect.getDataFolder(), name))
                .filter(File::isFile)
                .findFirst()
                .orElse(null);
    }
}
