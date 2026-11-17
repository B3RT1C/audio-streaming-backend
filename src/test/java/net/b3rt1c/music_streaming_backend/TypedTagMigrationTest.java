package net.b3rt1c.music_streaming_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TypedTagMigrationTest {

    @Test
    void movesExistingTagsToFreeWithoutRewritingTracks() throws Exception {
        try (Connection connection = open("typed-tags")) {
            migrateThroughV3(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO audio_data (name, storage_key, extension, path, title, secondary_title, artist, album)
                    VALUES ('t1', 'k1', 'mp3', 'uploads/songs/', 'Canción', 'Other', 'José', 'Álbum')
                    """);
                statement.executeUpdate("""
                    INSERT INTO tags (name, name_key) VALUES ('Live', 'live'), ('En Vivo', 'en vivo')
                    """);
            }

            run(connection, "/db/migration/V4__app_0_4_1_typed_tags.sql");

            try (Statement statement = connection.createStatement()) {
                assertEquals("Canción", scalar(statement, "SELECT title FROM audio_data WHERE storage_key = 'k1'"));
                assertEquals("Other", scalar(statement, "SELECT secondary_title FROM audio_data WHERE storage_key = 'k1'"));
                assertEquals("José", scalar(statement, "SELECT artist FROM audio_data WHERE storage_key = 'k1'"));
                assertEquals("Álbum", scalar(statement, "SELECT album FROM audio_data WHERE storage_key = 'k1'"));
                assertEquals("t1", scalar(statement, "SELECT name FROM audio_data WHERE storage_key = 'k1'"));
                assertNull(scalar(statement, "SELECT genre FROM audio_data WHERE storage_key = 'k1'"));
                assertNull(scalar(statement, "SELECT \"year\" FROM audio_data WHERE storage_key = 'k1'"));

                assertEquals(6, count(statement, "SELECT COUNT(*) FROM tag_types WHERE is_system = TRUE"));
                assertEquals(0, count(statement, "SELECT COUNT(*) FROM tag_types WHERE is_system = FALSE"));
                Set<String> keys = new HashSet<>();
                try (ResultSet rows = statement.executeQuery("SELECT type_key FROM tag_types")) {
                    while (rows.next()) {
                        keys.add(rows.getString(1));
                    }
                }
                assertEquals(Set.of("artist", "genre", "album", "year", "language", "free"), keys);
                assertEquals("Libre", scalar(statement, "SELECT name FROM tag_types WHERE type_key = 'free'"));
                assertEquals("Género", scalar(statement, "SELECT name FROM tag_types WHERE type_key = 'genre'"));
                assertEquals("Álbum", scalar(statement, "SELECT name FROM tag_types WHERE type_key = 'album'"));
                assertEquals("Año", scalar(statement, "SELECT name FROM tag_types WHERE type_key = 'year'"));

                assertEquals(2, count(statement, """
                    SELECT COUNT(*) FROM tags t
                    JOIN tag_types tt ON tt.id = t.type_id
                    WHERE tt.type_key = 'free'
                    """));
                assertEquals("free", scalar(statement, """
                    SELECT tt.type_key FROM tags t
                    JOIN tag_types tt ON tt.id = t.type_id
                    WHERE t.name_key = 'live'
                    """));
                assertEquals("free", scalar(statement, """
                    SELECT tt.type_key FROM tags t
                    JOIN tag_types tt ON tt.id = t.type_id
                    WHERE t.name = 'En Vivo'
                    """));

                int genreId = count(statement, "SELECT id FROM tag_types WHERE type_key = 'genre'");
                int freeId = count(statement, "SELECT id FROM tag_types WHERE type_key = 'free'");
                statement.executeUpdate(
                    "INSERT INTO tags (name, name_key, type_id) VALUES ('Rock', 'rock', " + genreId + ")"
                );
                statement.executeUpdate(
                    "INSERT INTO tags (name, name_key, type_id) VALUES ('Rock', 'rock', " + freeId + ")"
                );
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO tags (name, name_key, type_id) VALUES ('ROCK', 'rock', " + genreId + ")"
                ));
            }
        }
    }

    @Test
    void emptyLibraryStillGainsTheSystemTypes() throws Exception {
        try (Connection connection = open("typed-tags-empty")) {
            migrateThroughV3(connection);
            run(connection, "/db/migration/V4__app_0_4_1_typed_tags.sql");
            try (Statement statement = connection.createStatement()) {
                assertEquals(0, count(statement, "SELECT COUNT(*) FROM tags"));
                assertEquals(6, count(statement, "SELECT COUNT(*) FROM tag_types"));
                assertEquals(0, count(statement, "SELECT COUNT(*) FROM audio_data"));
            }
        }
    }

    @Test
    void migrationDoesNotRewriteAudioRows() throws Exception {
        String sql = new String(
            TypedTagMigrationTest.class.getResourceAsStream(
                "/db/migration/V4__app_0_4_1_typed_tags.sql"
            ).readAllBytes(),
            StandardCharsets.UTF_8
        ).toLowerCase().replaceAll("(?m)--.*$", "");
        assertFalse(sql.contains("update audio_data"));
        assertFalse(sql.contains("secondary_title"));
        assertTrue(sql.contains("update tags"));
        assertTrue(sql.contains("type_key"));
        assertTrue(sql.contains("'free'"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("delete "));
    }

    private static void migrateThroughV3(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE audio_data (
                    id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    name VARCHAR(500) NOT NULL,
                    storage_key VARCHAR(255) NOT NULL UNIQUE,
                    extension VARCHAR(255) NOT NULL,
                    path VARCHAR(4096) NOT NULL,
                    content_hash VARCHAR(255),
                    title VARCHAR(500) NOT NULL,
                    artist VARCHAR(500),
                    album VARCHAR(500),
                    duration_seconds INTEGER,
                    track_number INTEGER,
                    original_filename VARCHAR(500),
                    original_path VARCHAR(4096),
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            statement.execute("""
                CREATE TABLE playlists (
                    id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        run(connection, "/db/migration/V3__app_0_3_0_secondary_title_artists_and_tags.sql");
    }

    private static Connection open(String name) throws Exception {
        return DriverManager.getConnection(
            "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
    }

    private static void run(Connection connection, String resource) throws Exception {
        String sql = new String(
            TypedTagMigrationTest.class.getResourceAsStream(resource).readAllBytes(),
            StandardCharsets.UTF_8
        );
        StringBuilder statementText = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            statementText.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String command = statementText.toString().trim();
                command = command.substring(0, command.length() - 1);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(command);
                }
                statementText.setLength(0);
            }
        }
    }

    private static int count(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String scalar(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
