# Audio Streaming Backend

API central del ecosistema [audio-streaming](https://github.com/B3RT1C/audio-streaming).

- Repo: https://github.com/B3RT1C/audio-streaming-backend
- Stack: Java 25, Spring Boot, PostgreSQL
- Contrato: [`docs/openapi.yaml`](./docs/openapi.yaml)
- Versión: **0.4.0**

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
V3__app_0_3_0_secondary_title_artists_and_tags.sql
```

0.4.0 no añade migración. Los filtros reutilizan el plegado de texto de `q` (sin `unaccent` ni `pg_trgm`) y los índices de etiquetas de V3. Con 7–9 mil canciones el listado sigue siendo una lectura de la biblioteca; no se reescriben títulos, artistas ni datos importados.

- Si renombras una migración **ya aplicada**, hay que reparar `flyway_schema_history` (o resetear la DB local). No renombres en staging/prod sin repair.
- Seed demo (`data.sql`): solo con profile `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Staging/CI no activan ese profile (sin seed).

## API v0.4.0

Errores JSON: `{ "message": "...", "code": "NOT_FOUND" | "FILE_TOO_LARGE" | ... }`.
Detalle: [`docs/openapi.yaml`](./docs/openapi.yaml).

### Audios

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/audios` | Lista. Los filtros de abajo se combinan con AND. Un parámetro vacío se ignora. Si no hay coincidencias, la lista va vacía (no es un error). Sin `shuffle`, el orden sigue siendo por `id`. No hay paginación |
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
| `secondaryTitle` | Título secundario (traducción u otro idioma), o `null`. Siempre viene en el JSON |
| `artist` | Artista, o `null`. Sigue siendo texto: no es el id de `/artists` |
| `album` | Álbum, o `null` |
| `durationSeconds` | Duración en segundos, o `null` si no se pudo leer |
| `trackNumber` | Número de pista, o `null` |
| `originalFilename` | Nombre original del archivo |
| `createdAt` | Fecha de alta, ISO-8601 |
| `tags` | Nombres de etiquetas, ordenados sin distinguir mayúsculas. Siempre es un array; vacío si no hay |

`PATCH` de ejemplo:

```json
{ "title": "Nuevo título", "secondaryTitle": "Other language", "artist": "Artista", "album": null, "trackNumber": 3 }
```

`null` borra título secundario, artista, álbum, número de pista o duración. Un título secundario en blanco se guarda como `null`. El título y el nombre no se pueden dejar vacíos. Un campo `tags` dentro del `PATCH` se ignora: las etiquetas se cambian con `PUT`.

### Etiquetas y artistas

La migración `V3` añade `secondary_title` (nullable) y no reescribe las filas que ya existen. Crea `artists` con los `audios.artist` distintos que no están vacíos, copiando el texto tal cual (espacios y mayúsculas incluidos). No modifica `audios.artist`, ni los títulos, ni el resto de lo importado.

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/tags` | Catálogo. Cada etiqueta trae `id`, `name`, `audioCount`, `artistCount` y `playlistCount`. Una etiqueta sin usos se queda, con ceros |
| GET | `/artists` | Artistas con `id`, `name`, `audioCount` y `tags`. `audioCount` cuenta canciones cuyo `artist` es exactamente `name`. `Ana` y `ana` son dos artistas. No hay `POST`: aparecen con la migración y cuando una canción se sube, se importa o se edita el artista. `q` opcional: subcadena del nombre, sin mayúsculas ni acentos. `tag` repetible: el artista tiene que tener todas. El orden no cambia |
| PUT | `/audios/{id}/tags` | Sustituye las etiquetas de la canción. Cuerpo `{ "tags": ["Live", "rock"] }`. Crea las que no existan. `[]` las quita de la canción. Responde la canción |
| PUT | `/artists/{id}/tags` | Igual, para un artista. No cambia el nombre ni el texto de las canciones. Responde el artista |
| PUT | `/playlists/{id}/tags` | Igual, para una playlist. Responde la playlist con `items` y `tags`, y actualiza `updatedAt` |
| GET | `/audios?tag=Live` | Una sola etiqueta se comporta como en 0.3.0: nombre exacto, sin distinguir mayúsculas (los acentos sí). `?tag=a&tag=b` exige las dos. Se puede combinar con `q` y con el resto de filtros |

El nombre de una etiqueta tiene como mucho 100 caracteres, y cada asignación como mucho 100 nombres. `Live` y `LIVE` son la misma etiqueta: se conserva la grafía con la que se creó. Los acentos distinguen (`José` y `jose` son etiquetas distintas). La comparación de `q` sigue sin distinguir acentos, también en `secondaryTitle`.

### Filtros (0.4.0)

En `GET /audios`, además de `q` y `tag`:

| Parámetro | Regla |
|-----------|--------|
| `title` | Subcadena de `title` o `secondaryTitle`, sin mayúsculas ni acentos. No mira artista, álbum, nombre ni archivo |
| `artist` | Subcadena de `audios.artist`, sin mayúsculas ni acentos. Una canción sin artista no entra |
| `tag` | Repetible. La canción tiene que tener todas las etiquetas. Una sola vale como en 0.3.0 |
| `artistTag` | Repetible. La fila de `artists` cuyo `name` es exactamente `audios.artist` tiene que tener todas. `Ana` no hereda las etiquetas de `ana` |
| `playlistTag` | Repetible. La canción está en alguna playlist que tiene todas esas etiquetas. No vale repartirlas entre playlists |
| `shuffle=true` | Devuelve el resultado ya filtrado, en orden aleatorio. `seed` entero opcional repite la misma mezcla. Sin `shuffle`, el orden por `id` |

`shuffle` distinto de `true` o `false`, o un `seed` que no sea entero cuando `shuffle=true`, responde **400** con código `INVALID_QUERY`. Un `seed` suelto, sin barajar, se ignora.

`GET /playlists` acepta `q` (subcadena del nombre, sin mayúsculas ni acentos) y `tag` repetible (la playlist tiene que tener todas). Sigue ordenada por `id` y sin `items`.

`GET /artists` acepta `q` (subcadena del nombre, sin mayúsculas ni acentos) y `tag` repetible (AND). El orden de los que quedan es el de siempre.

`tags` va en la canción (lista, `PATCH` y los tres `PUT` de etiquetas), en el artista y en la playlist (también en `GET /playlists`, donde `items` sigue sin venir). `GET /audios/{id}` sigue siendo el stream, no el JSON.

Las etiquetas de una playlist también salen en cada `audio` de `items`. Borrar una canción o una playlist quita sus vínculos; no borra la etiqueta ni, en el caso del artista, la fila de `artists`.

### Playlists

| Método | Ruta | Cuerpo | Respuesta |
|--------|------|--------|-----------|
| GET | `/playlists` | | Lista sin canciones (`itemCount`, sin `items`) y con `tags`. `q` opcional busca en el nombre; `tag` repetible exige todas. Orden por `id` |
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
java -jar music-streaming-backend-0.4.0.jar \
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
