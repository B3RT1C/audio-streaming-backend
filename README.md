# Audio Streaming Backend

API central del ecosistema [audio-streaming](https://github.com/B3RT1C/audio-streaming).

- Repo: https://github.com/B3RT1C/audio-streaming-backend
- Stack: Java 25, Spring Boot, PostgreSQL
- Contrato: [`docs/openapi.yaml`](./docs/openapi.yaml)

## Requisitos

- JDK 25+
- Maven 3.9+ (o `./mvnw`)
- PostgreSQL 16 (o Docker)

## Arranque

Con Postgres en `localhost:5432` (usuario/clave `postgres`, DB `music-streaming-db`):

```bash
./mvnw spring-boot:run
```

API en `http://localhost:8080`.

Variables opcionales:

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/music-streaming-db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |

## API v0.1.0

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/song` | Listar canciones |
| GET | `/song/file?id=` | Stream con HTTP Range |
| POST | `/song/file` | Subir MP3 |
| DELETE | `/song?id=` | Borrar canción y fichero |

Detalle: [`docs/openapi.yaml`](./docs/openapi.yaml).

## Tests

```bash
./mvnw test
```

Los tests de contexto usan H2 (no requieren Postgres externo).

## Cliente web

https://github.com/B3RT1C/audio-streaming-web
