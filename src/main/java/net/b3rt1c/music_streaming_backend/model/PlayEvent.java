package net.b3rt1c.music_streaming_backend.model;

import java.time.Instant;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "play_events",
    uniqueConstraints = @UniqueConstraint(
        name = "play_events_client_event_id_key",
        columnNames = "client_event_id"
    ),
    indexes = {
        @Index(name = "play_events_started_at_idx", columnList = "started_at"),
        @Index(name = "play_events_audio_id_idx", columnList = "audio_id")
    }
)
@Check(constraints = """
    listened_seconds >= 0
    AND (source IS NULL OR source IN ('library', 'search', 'playlist', 'artist', 'shuffle', 'other'))
    """)
@Getter
@Setter
@NoArgsConstructor
public class PlayEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(
        name = "audio_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "play_events_audio_fk",
            foreignKeyDefinition = "foreign key (audio_id) references audio_data (id) on delete cascade"
        )
    )
    private AudioData audio;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "listened_seconds", nullable = false)
    private int listenedSeconds;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private boolean counted;

    @Column(columnDefinition = "text")
    private String source;

    @ManyToOne
    @JoinColumn(
        name = "playlist_id",
        foreignKey = @ForeignKey(
            name = "play_events_playlist_fk",
            foreignKeyDefinition = "foreign key (playlist_id) references playlists (id) on delete set null"
        )
    )
    private Playlist playlist;

    @Column(name = "client_event_id", columnDefinition = "text")
    private String clientEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (startedAt == null) {
            startedAt = now;
        }
    }
}
