package net.b3rt1c.music_streaming_backend.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "app.stats")
public class StatsProperties {
    /**
     * Calendar zone for {@code from}/{@code to} and timeline buckets.
     */
    private String zone = "Europe/Madrid";

    /**
     * A listen counts when the stored seconds reach this value, or a shorter
     * fraction of the track when the track itself is shorter.
     */
    private int minCountedSeconds = 30;

    /**
     * Fraction of the known duration used together with {@link #minCountedSeconds}.
     * The threshold is the smaller of the two.
     */
    private double countedDurationRatio = 0.5;

    /**
     * Stored listened time is capped at the known duration plus this many seconds.
     */
    private int listenedSlackSeconds = 5;

    @PostConstruct
    void validate() {
        try {
            ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new IllegalStateException("app.stats.zone is not a time zone: " + zone, exception);
        }
        if (minCountedSeconds < 0) {
            throw new IllegalStateException("app.stats.min-counted-seconds must be zero or greater");
        }
        if (Double.isNaN(countedDurationRatio) || countedDurationRatio < 0) {
            throw new IllegalStateException("app.stats.counted-duration-ratio must be zero or greater");
        }
        if (listenedSlackSeconds < 0) {
            throw new IllegalStateException("app.stats.listened-slack-seconds must be zero or greater");
        }
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone == null ? "" : zone.trim();
    }

    public int getMinCountedSeconds() {
        return minCountedSeconds;
    }

    public void setMinCountedSeconds(int minCountedSeconds) {
        this.minCountedSeconds = minCountedSeconds;
    }

    public double getCountedDurationRatio() {
        return countedDurationRatio;
    }

    public void setCountedDurationRatio(double countedDurationRatio) {
        this.countedDurationRatio = countedDurationRatio;
    }

    public int getListenedSlackSeconds() {
        return listenedSlackSeconds;
    }

    public void setListenedSlackSeconds(int listenedSlackSeconds) {
        this.listenedSlackSeconds = listenedSlackSeconds;
    }
}
