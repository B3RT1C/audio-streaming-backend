/**
 * Validates docs/openapi.yaml (v0.1 paths) against AudioController mappings.
 * Run: node scripts/ci/check-openapi-contract.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const openapiPath = path.join(root, 'docs', 'openapi.yaml');
const controllerPath = path.join(
  root,
  'src/main/java/net/b3rt1c/music_streaming_backend/controller/AudioController.java',
);

const REQUIRED_PATHS = ['/audios', '/audios/{id}'];
const REQUIRED_METHODS = {
  '/audios': ['get', 'post'],
  '/audios/{id}': ['get', 'delete'],
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
    // left the paths block
    if (line.match(/^[a-zA-Z]/) || line.match(/^components:/)) {
      current = null;
    }
  }
  return paths;
}

const yaml = fs.readFileSync(openapiPath, 'utf8');
if (!yaml.includes('openapi:')) fail('docs/openapi.yaml missing openapi version');

const apiPaths = extractOpenApiPaths(yaml);
for (const p of REQUIRED_PATHS) {
  if (!apiPaths[p]) fail(`openapi missing path ${p}`);
  for (const m of REQUIRED_METHODS[p]) {
    if (!apiPaths[p].has(m)) fail(`openapi ${p} missing method ${m}`);
  }
}

const controller = fs.readFileSync(controllerPath, 'utf8');
if (!controller.includes('@RequestMapping("/audios")')) fail('AudioController must map /audios');
if (!controller.includes('@GetMapping')) fail('AudioController missing @GetMapping');
if (!controller.includes('@GetMapping("/{id}")')) fail('AudioController missing GET /{id}');
if (!controller.includes('@PostMapping')) fail('AudioController missing @PostMapping');
if (!controller.includes('@DeleteMapping("/{id}")')) fail('AudioController missing DELETE /{id}');

console.log('CONTRACT OK: openapi v0.1 paths match AudioController');
for (const p of REQUIRED_PATHS) {
  console.log(`  ${p}: ${[...apiPaths[p]].sort().join(', ')}`);
}
