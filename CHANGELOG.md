# Changelog

## 0.4.1

- Tipos de etiqueta. Los de sistema (`artist`, `genre`, `album`, `year`, `language`, `free`) no se renombran ni se borran. El usuario puede crear tipos propios, renombrarlos y borrarlos; al borrarlos se borran sus etiquetas.
- Las etiquetas que ya existían pasan al tipo `free`. La unicidad pasa a ser por tipo y nombre, sin distinguir mayúsculas.
- `genre`, `album` y `year` se derivan de la canción. No se asignan ni se quitan a mano. `artist` no se duplica como etiqueta: el filtro usa la tabla de artistas. `language` se asigna a mano.
- `POST /admin/tags/rebuild-system` recalcula género, año y las etiquetas derivadas leyendo los ficheros. No modifica título, título secundario ni artista. Se puede repetir.
- `GET /audios` acepta `f` repetible (`artista:`, `género:`, `álbum:`, `año:` con rango, `idioma:`, `playlist:`, un tipo de usuario, o una etiqueta libre). Se combina con los filtros de 0.4.0.
- `GET /filter/suggest` autocompleta valores de todos los tipos, artistas y playlists.
- Columnas nuevas `genre` y `year` en `audio_data`, más índices para buscar por tipo de etiqueta y por año.

## 0.4.0

- Filtros de canciones por `title`, `artist`, `tag`, `artistTag` y `playlistTag`, combinados con AND.
- `shuffle` con `seed` opcional.
- `q` y `tag` en `GET /artists` y `GET /playlists`.
