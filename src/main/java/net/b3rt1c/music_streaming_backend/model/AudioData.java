package net.b3rt1c.music_streaming_backend.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AudioData implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Setter
    @Column(nullable = false, length = 500)
    private String name;

    @Setter
    @Column(nullable = false, length = 500)
    private String title;

    @Setter
    @Column(name = "secondary_title", length = 500)
    private String secondaryTitle;

    @Setter
    @Column(length = 500)
    private String artist;

    @Setter
    @Column(length = 500)
    private String album;

    @Setter
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Setter
    @Column(name = "track_number")
    private Integer trackNumber;

    @Setter
    @JsonIgnore
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Setter
    @Column(nullable = false)
    private String extension;

    @Setter
    @Column(nullable = false, length = 4096)
    private String path;

    @Setter
    @Column(name = "content_hash")
    private String contentHash;

    @Setter
    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Setter
    @JsonIgnore
    @Column(name = "original_path", length = 4096)
    private String originalPath;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @BatchSize(size = 100)
    @ManyToMany
    @JoinTable(
        name = "audio_tags",
        joinColumns = @JoinColumn(name = "audio_id", nullable = false),
        inverseJoinColumns = @JoinColumn(name = "tag_id", nullable = false)
    )
    private Set<Tag> tags = new LinkedHashSet<>();

    public AudioData(String name, String storageKey, String extension, String path) {
        this.name = name;
        this.storageKey = storageKey;
        this.extension = extension;
        this.path = path;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (title == null || title.isBlank()) {
            title = name;
        }
    }
}
