package net.b3rt1c.music_streaming_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "tags",
    uniqueConstraints = @UniqueConstraint(name = "tags_name_key_key", columnNames = "name_key")
)
@Getter
@Setter
@NoArgsConstructor
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;
}
