package net.b3rt1c.music_streaming_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    /**
     * Directory for new audio files. Blank keeps the historical relative path {@code uploads/songs/}.
     */
    private String dir = "";

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
}
