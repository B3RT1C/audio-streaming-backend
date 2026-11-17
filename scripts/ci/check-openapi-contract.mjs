/**
 * Validates docs/openapi.yaml against the controller mappings.
 * Run: node scripts/ci/check-openapi-contract.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const openapiPath = path.join(root, 'docs', 'openapi.yaml');
const controllerDir = path.join(root, 'src/main/java/net/b3rt1c/music_streaming_backend/controller');

const REQUIRED_PATHS = [
  '/audios',
  '/audios/{id}',
  '/audios/{id}/tags',
  '/tags',
  '/artists',
  '/artists/{id}/tags',
  '/playlists',
  '/playlists/{id}',
  '/playlists/{id}/tags',
  '/playlists/{id}/items',
  '/playlists/{id}/items/{audioId}',
  '/admin/import',
];
const REQUIRED_METHODS = {
  '/audios': ['get', 'post'],
  '/audios/{id}': ['get', 'patch', 'delete'],
  '/audios/{id}/tags': ['put'],
  '/tags': ['get'],
  '/artists': ['get'],
  '/artists/{id}/tags': ['put'],
  '/playlists': ['get', 'post'],
  '/playlists/{id}': ['get', 'patch', 'delete'],
  '/playlists/{id}/tags': ['put'],
  '/playlists/{id}/items': ['post', 'put'],
  '/playlists/{id}/items/{audioId}': ['delete'],
  '/admin/import': ['post'],
};

function fail(msg) {
  console.error(`CONTRACT FAIL: ${msg}`);
  process.exit(1);
}

function extractOpenApiPaths(yaml) {
  const paths = {};
  let current = null;
  for (const line of yaml.split(/\r?\n/)) {
    const pathMatch = line.match(/^  (\/[A-Za-z0-9_{}\/-]+):\s*$/);
    if (pathMatch) {
      current = pathMatch[1];
      paths[current] = new Set();
      continue;
    }
    if (!current) continue;
    const methodMatch = line.match(/^    (get|post|put|patch|delete):\s*$/);
    if (methodMatch) {
      paths[current].add(methodMatch[1]);
      continue;
    }
    if (line.match(/^[a-zA-Z]/) || line.match(/^components:/)) {
      current = null;
    }
  }
  return paths;
}

const yaml = fs.readFileSync(openapiPath, 'utf8');
if (!yaml.includes('openapi:')) fail('docs/openapi.yaml missing openapi version');
if (!yaml.includes('version: 0.3.0')) fail('openapi version must be 0.3.0');

const apiPaths = extractOpenApiPaths(yaml);
for (const p of REQUIRED_PATHS) {
  if (!apiPaths[p]) fail(`openapi missing path ${p}`);
  for (const m of REQUIRED_METHODS[p]) {
    if (!apiPaths[p].has(m)) fail(`openapi ${p} missing method ${m}`);
  }
}

const audio = fs.readFileSync(path.join(controllerDir, 'AudioController.java'), 'utf8');
if (!audio.includes('@RequestMapping("/audios")')) fail('AudioController must map /audios');
if (!audio.includes('@GetMapping')) fail('AudioController missing @GetMapping');
if (!audio.includes('@GetMapping("/{id}")')) fail('AudioController missing GET /{id}');
if (!audio.includes('@PostMapping')) fail('AudioController missing @PostMapping');
if (!audio.includes('@PatchMapping("/{id}")')) fail('AudioController missing PATCH /{id}');
if (!audio.includes('@DeleteMapping("/{id}")')) fail('AudioController missing DELETE /{id}');
if (!audio.includes('@PutMapping("/{id}/tags")')) fail('AudioController missing PUT /{id}/tags');

const patch = fs.readFileSync(
  path.join(root, 'src/main/java/net/b3rt1c/music_streaming_backend/dto/AudioMetadataPatch.java'),
  'utf8',
);
if (!patch.includes('secondaryTitle')) fail('AudioMetadataPatch must accept secondaryTitle');

const playlists = fs.readFileSync(path.join(controllerDir, 'PlaylistController.java'), 'utf8');
if (!playlists.includes('@RequestMapping("/playlists")')) fail('PlaylistController must map /playlists');
if (!playlists.includes('@PostMapping("/{id}/items")')) fail('PlaylistController missing POST items');
if (!playlists.includes('@PutMapping("/{id}/items")')) fail('PlaylistController missing PUT items');
if (!playlists.includes('@DeleteMapping("/{id}/items/{audioId}")')) fail('PlaylistController missing DELETE item');
if (!playlists.includes('@PutMapping("/{id}/tags")')) fail('PlaylistController missing PUT tags');

const tags = fs.readFileSync(path.join(controllerDir, 'TagController.java'), 'utf8');
if (!tags.includes('@RequestMapping("/tags")')) fail('TagController must map /tags');
if (!tags.includes('@GetMapping')) fail('TagController missing GET');

const artists = fs.readFileSync(path.join(controllerDir, 'ArtistController.java'), 'utf8');
if (!artists.includes('@RequestMapping("/artists")')) fail('ArtistController must map /artists');
if (!artists.includes('@GetMapping')) fail('ArtistController missing GET');
if (!artists.includes('@PutMapping("/{id}/tags")')) fail('ArtistController missing PUT tags');

const importer = fs.readFileSync(path.join(controllerDir, 'ImportController.java'), 'utf8');
if (!importer.includes('@RequestMapping("/admin")')) fail('ImportController must map /admin');
if (!importer.includes('@PostMapping("/import")')) fail('ImportController missing POST /import');

const cors = fs.readFileSync(
  path.join(root, 'src/main/java/net/b3rt1c/music_streaming_backend/config/CorsConfig.java'),
  'utf8',
);
if (cors.includes('192.168.')) fail('CORS still has a hardcoded LAN address');

console.log('CONTRACT OK: openapi v0.3.0 paths match the controllers');
for (const p of REQUIRED_PATHS) {
  console.log(`  ${p}: ${[...apiPaths[p]].sort().join(', ')}`);
}
