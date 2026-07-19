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

### Persistencia

- Esquema: **Flyway** (`src/main/resources/db/migration/`). Hibernate usa `ddl-auto=validate` (no crea ni borra tablas).
- Los datos de usuario **no** se recrean al arrancar; cada cambio de modelo va en una migración nueva.
- Naming: `V{seq}__app_{x}_{y}_{z}_{descripcion}.sql` — `{seq}` ordena Flyway; `app_x_y_z` indica la versión de producto en la que entró el cambio.

```
V1__app_0_1_0_create_audio_data.sql   ← esquema inicial (app 0.1.0)
V2__app_0_1_0_…                       ← otro cambio de esquema en 0.1.0
V3__app_0_2_0_…                       ← primer cambio de esquema en 0.2.0
```

- Si renombras una migración **ya aplicada**, hay que reparar `flyway_schema_history` (o resetear la DB local). No renombres en staging/prod sin repair.
- Seed demo (`data.sql`): solo con profile `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Staging/CI no activan ese profile (sin seed).

## API v0.1.0

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/audios` | Listar canciones |
| GET | `/audios/{id}` | Stream con HTTP Range |
| POST | `/audios` | Subir MP3 (`file` + `name` opcional; nombres repetibles) |
| DELETE | `/audios/{id}` | Borrar canción y fichero |

Errores JSON: `{ "message": "...", "code": "NOT_FOUND" | "FILE_REQUIRED" | ... }`.  
Detalle: [`docs/openapi.yaml`](./docs/openapi.yaml).

## Tests

```bash
./mvnw test
```

Los tests de contexto usan H2 con `create-drop` y Flyway desactivado (no requieren Postgres externo).

## Cliente web

https://github.com/B3RT1C/audio-streaming-web
