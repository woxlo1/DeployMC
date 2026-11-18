import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { precompressDirectory } from './precompress.mjs';

const directories = [];

afterEach(() => {
  while (directories.length > 0) {
    rmSync(directories.pop(), { recursive: true, force: true });
  }
});

describe('precompressDirectory', () => {
  it('writes smaller variants only for supported assets', () => {
    const directory = mkdtempSync(path.join(os.tmpdir(), 'deploymc-web-'));
    directories.push(directory);
    mkdirSync(path.join(directory, 'assets'));
    const source = 'console.log("DeployMC");\n'.repeat(40);
    const asset = path.join(directory, 'assets', 'index-abcdef12.js');
    const sourceMap = path.join(directory, 'assets', 'index-abcdef12.js.map');
    writeFileSync(asset, source);
    writeFileSync(sourceMap, JSON.stringify({ version: 3, sources: ['index.ts'] }));

    const result = precompressDirectory(directory);

    expect(result).toEqual({ gzipped: 1, brotli: 1 });
    expect(existsSync(`${asset}.gz`)).toBe(true);
    expect(existsSync(`${asset}.br`)).toBe(true);
    expect(existsSync(`${sourceMap}.gz`)).toBe(false);
    expect(readFileSync(`${asset}.gz`).length).toBeLessThan(Buffer.byteLength(source));
  });
});
