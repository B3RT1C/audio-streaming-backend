package net.b3rt1c.music_streaming_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {
    /**
     * Only folders inside this directory can be imported. Blank disables folder import.
     */
    private String dir = "";

    /**
     * When set (for example {@code --app.import.path=...}), that folder is imported once at startup.
     */
    private String path = "";

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
