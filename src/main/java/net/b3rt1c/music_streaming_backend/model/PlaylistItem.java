package net.b3rt1c.music_streaming_backend.model;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "playlist_items",
    uniqueConstraints = @UniqueConstraint(
        name = "playlist_items_audio_unique",
        columnNames = {"playlist_id", "audio_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class PlaylistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audio_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AudioData audio;

    @Column(name = "sort_order", nullable = false)
    private int position;
}
