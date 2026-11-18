import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import zlib from 'node:zlib';

const COMPRESSIBLE_EXTENSIONS = new Set(['.css', '.js', '.json', '.mjs', '.svg', '.txt', '.wasm']);

/** Writes precompressed variants beside compressible build outputs when they are smaller. */
export function precompressDirectory(directory) {
  const result = { gzipped: 0, brotli: 0 };

  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      const nested = precompressDirectory(target);
      result.gzipped += nested.gzipped;
      result.brotli += nested.brotli;
      continue;
    }
    if (!entry.isFile() || !COMPRESSIBLE_EXTENSIONS.has(path.extname(entry.name).toLowerCase())) {
      continue;
    }

    const source = readFileSync(target);
    const variants = [
      {
        suffix: '.gz',
        bytes: zlib.gzipSync(source, { level: zlib.constants.Z_BEST_COMPRESSION }),
        key: 'gzipped',
      },
      {
        suffix: '.br',
        bytes: zlib.brotliCompressSync(source),
        key: 'brotli',
      },
    ];

    for (const variant of variants) {
      if (variant.bytes.length < source.length) {
        writeFileSync(`${target}${variant.suffix}`, variant.bytes);
        result[variant.key] += 1;
      }
    }
  }

  return result;
}

function main() {
  const dist = path.resolve(process.cwd(), 'dist');
  const result = precompressDirectory(dist);
  console.log(`Precompressed ${result.gzipped} gzip and ${result.brotli} Brotli assets in ${dist}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main();
}
