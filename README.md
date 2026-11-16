# Audio Streaming Backend

API central del ecosistema [audio-streaming](https://github.com/B3RT1C/audio-streaming).

- Repo: https://github.com/B3RT1C/audio-streaming-backend
- Stack: Java 25, Spring Boot, PostgreSQL
- Contrato: [`docs/openapi.yaml`](./docs/openapi.yaml)
- Versión: **0.2.0**

No hay login. El servicio está pensado para usarse solo dentro de la red privada.

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

### Variables de entorno

| Variable | Default | Uso |
|----------|---------|-----|
| `DB_URL` | `jdbc:postgresql://localhost:5432/music-streaming-db` | JDBC |
| `DB_USERNAME` | `postgres` | Usuario de la base |
| `DB_PASSWORD` | `postgres` | Clave de la base |
| `AUDIO_STORAGE_DIR` | vacío | Carpeta de los audios. Si no se define, se sigue usando la ruta relativa `uploads/songs/` (el despliegue actual no cambia). Si se define, se guarda como ruta absoluta. |
| `AUDIO_MAX_FILE_SIZE` | `500MB` | Tamaño máximo de un fichero |
| `AUDIO_MAX_REQUEST_SIZE` | `510MB` | Tamaño máximo de la petición. Debe ser al menos `AUDIO_MAX_FILE_SIZE` |
| `CORS_ALLOWED_ORIGINS` | `*` | Orígenes del navegador, separados por comas. `*` permite cualquiera |
| `AUDIO_IMPORT_DIR` | vacío | Carpeta base de la importación. Si está vacía, `POST /admin/import` responde `IMPORT_NOT_CONFIGURED` |

Ejemplo para guardar la biblioteca fuera del directorio de trabajo:

```bash
AUDIO_STORAGE_DIR=D:\audio-library
AUDIO_IMPORT_DIR=D:\musica
```

### Persistencia

- Esquema: **Flyway** (`src/main/resources/db/migration/`). Hibernate usa `ddl-auto=validate` (no crea ni borra tablas).
- Los datos de usuario **no** se recrean al arrancar; cada cambio de modelo va en una migración nueva.
- Naming: `V{seq}__app_{x}_{y}_{z}_{descripcion}.sql` — `{seq}` ordena Flyway; `app_x_y_z` indica la versión de producto en la que entró el cambio.

```
V1__app_0_1_0_create_audio_data.sql
V2__app_0_2_0_library_metadata_and_playlists.sql
```

- Si renombras una migración **ya aplicada**, hay que reparar `flyway_schema_history` (o resetear la DB local). No renombres en staging/prod sin repair.
- Seed demo (`data.sql`): solo con profile `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Staging/CI no activan ese profile (sin seed).

## API v0.2.0

Errores JSON: `{ "message": "...", "code": "NOT_FOUND" | "FILE_TOO_LARGE" | ... }`.
Detalle: [`docs/openapi.yaml`](./docs/openapi.yaml).

### Audios

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/audios` | Lista. `q` opcional: busca sin distinguir mayúsculas ni acentos en título, artista, álbum, nombre y nombre de archivo |
| GET | `/audios/{id}` | Stream. `Content-Type` según la extensión. Un rango fuera del fichero responde **416** |
| POST | `/audios` | Sube un audio (`file` + `name` opcional). Sigue respondiendo `{ "message": "File received" }` |
| PATCH | `/audios/{id}` | Edita metadatos. Solo cambian los campos enviados |
| DELETE | `/audios/{id}` | Borra la canción, el fichero y la quita de las playlists |

Extensiones admitidas: `mp3`, `m4a`, `aac`, `flac`, `wav`, `ogg`, `opus`, `webm`.

Al subir o importar se leen las etiquetas (jaudiotagger). Si no hay título, se usa el nombre del archivo sin extensión. Un fichero por encima del límite responde **413** con código `FILE_TOO_LARGE`.

Campos nuevos en cada canción, además de los de 0.1.0 (`id`, `name`, `extension`, `path`, `contentHash`):

| Campo | Significado |
|-------|-------------|
| `title` | Título |
| `artist` | Artista, o `null` |
| `album` | Álbum, o `null` |
| `durationSeconds` | Duración en segundos, o `null` si no se pudo leer |
| `trackNumber` | Número de pista, o `null` |
| `originalFilename` | Nombre original del archivo |
| `createdAt` | Fecha de alta, ISO-8601 |

`PATCH` de ejemplo:

```json
{ "title": "Nuevo título", "artist": "Artista", "album": null, "trackNumber": 3 }
```

`null` borra artista, álbum, número de pista o duración. El título y el nombre no se pueden dejar vacíos.

### Playlists

| Método | Ruta | Cuerpo | Respuesta |
|--------|------|--------|-----------|
| GET | `/playlists` | | Lista sin canciones (`itemCount`, sin `items`) |
| POST | `/playlists` | `{ "name": "Noche" }` | 201, playlist con `items: []` |
| GET | `/playlists/{id}` | | Playlist con `items` en orden |
| PATCH | `/playlists/{id}` | `{ "name": "Otro" }` | Playlist actualizada |
| DELETE | `/playlists/{id}` | | 204. No borra las canciones |
| POST | `/playlists/{id}/items` | `{ "audioId": 5 }` | 201 si se añade; 200 si ya estaba |
| DELETE | `/playlists/{id}/items/{audioId}` | | 204 |
| PUT | `/playlists/{id}/items` | `{ "audioIds": [3, 1, 2] }` | Reordena. También acepta el array `[3, 1, 2]` |

El reordenado tiene que incluir exactamente las canciones que ya están en la playlist, cada una una vez. `position` empieza en 1. El orden de reproducción es el del array `items`.

### Importar una carpeta

Copia los audios al almacenamiento. No mueve ni borra los originales. Recorre las subcarpetas, lee etiquetas y no duplica si la ruta original o el hash ya existen. Los ficheros que no son audio se ignoran.

Hay que definir `AUDIO_IMPORT_DIR`. La ruta pedida tiene que quedar dentro de esa carpeta.

Por HTTP, con el servidor ya arrancado:

```bash
curl -X POST http://localhost:8080/admin/import \
  -H "Content-Type: application/json" \
  -d "{\"path\":\"D:\\\\musica\"}"
```

Si `path` se omite, se importa `AUDIO_IMPORT_DIR` entero. Una ruta relativa se resuelve dentro de esa carpeta.

La respuesta es un resumen:

```json
{ "imported": 10, "skipped": 2, "failed": 0, "ignored": 4, "errors": [] }
```

Por línea de comandos, una sola vez al arrancar, y el proceso termina al acabar:

```bash
java -jar music-streaming-backend-0.2.0.jar \
  --app.import.path="D:\musica" \
  --spring.main.web-application-type=none
```

`AUDIO_IMPORT_DIR` tiene que contener esa ruta (puede ser la misma carpeta). El resumen queda en el log: `imported`, `skipped`, `failed`, `ignored`. Sin `--spring.main.web-application-type=none` el servidor sigue en marcha después de importar.

## Tests

```bash
./mvnw test
```

Los tests de contexto usan H2 con `create-drop` y Flyway desactivado (no requieren Postgres externo).

## Cliente web

https://github.com/B3RT1C/audio-streaming-web
