import assert from 'node:assert/strict';
import { access, mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { preparePagesSite } from './prepare-github-pages.mjs';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const staticSource = path.join(projectRoot, 'src/main/resources/static');
const pagesBase = new URL('https://juliomm8.github.io/bikestore-async/');

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

function assertPublishedAsset(reference, base, outputDir) {
  if (!reference || reference.startsWith('#') || reference.startsWith('data:')) return;
  const resolved = new URL(reference, base);
  if (resolved.origin !== pagesBase.origin) return;
  assert.ok(resolved.pathname.startsWith('/bikestore-async/'), `${reference} debe conservar la subruta del repositorio`);
  const relativePath = decodeURIComponent(resolved.pathname.slice('/bikestore-async/'.length));
  assert.ok(relativePath, `${reference} no debe resolver a la raíz de GitHub Pages`);
  return exists(path.join(outputDir, ...relativePath.split('/'))).then(found => {
    assert.ok(found, `No existe el recurso publicado ${reference}`);
  });
}

test('prepara solo el frontend y activa un modo estático sin formulario de pedido', async t => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'bikestore-pages-'));
  t.after(() => rm(temporaryRoot, { recursive: true, force: true }));
  const outputDir = path.join(temporaryRoot, 'site');

  await preparePagesSite({ sourceDir: staticSource, outputDir });
  const html = await readFile(path.join(outputDir, 'index.html'), 'utf8');
  const storeJs = await readFile(path.join(outputDir, 'js/store.js'), 'utf8');
  assert.match(html, /<meta name="bikestore-mode" content="static">/);
  assert.match(html, /id="static-demo-banner" aria-label="Demostración visual"/);
  assert.match(html, /id="static-order-note">/);
  assert.match(html, /<form id="order-form" novalidate hidden>/);
  assert.match(storeJs, /if \(staticDemo\) return;/);
  assert.ok(await exists(path.join(outputDir, '.nojekyll')));
  assert.equal(await exists(path.join(outputDir, 'src')), false);
  assert.equal(await exists(path.join(outputDir, 'docs')), false);
  assert.equal(await exists(path.join(outputDir, '.env')), false);
  assert.equal(await exists(path.join(outputDir, 'pom.xml')), false);
});

test('las rutas de HTML, CSS y las doce imágenes del catálogo resuelven bajo la subruta de Pages', async t => {
  const temporaryRoot = await mkdtemp(path.join(os.tmpdir(), 'bikestore-assets-'));
  t.after(() => rm(temporaryRoot, { recursive: true, force: true }));
  const outputDir = path.join(temporaryRoot, 'site');
  await preparePagesSite({ sourceDir: staticSource, outputDir });

  const html = await readFile(path.join(outputDir, 'index.html'), 'utf8');
  const css = await readFile(path.join(outputDir, 'css/store.css'), 'utf8');
  const catalog = await readFile(path.join(outputDir, 'js/catalog.js'), 'utf8');
  const htmlReferences = [...html.matchAll(/\b(?:src|href|srcset)="([^"]+)"/g)].map(match => match[1]);
  for (const reference of htmlReferences) {
    await assertPublishedAsset(reference, pagesBase, outputDir);
  }

  const cssBase = new URL('css/store.css', pagesBase);
  const cssReferences = [...css.matchAll(/url\(["']?([^)'"\s]+)["']?\)/g)].map(match => match[1]);
  for (const reference of cssReferences) {
    await assertPublishedAsset(reference, cssBase, outputDir);
  }

  const images = [...catalog.matchAll(/\bimage: '([^']+\.webp)'/g)].map(match => match[1]);
  assert.equal(images.length, 12, 'el catálogo conserva doce imágenes únicas');
  assert.equal(new Set(images).size, 12, 'cada modelo debe apuntar a su imagen individual');
  for (const reference of images) {
    await assertPublishedAsset(reference, pagesBase, outputDir);
  }

  assert.match(html, /store\.css\?v=20261031-8/);
  assert.match(css, /fonts\/cormorant-garamond-latin-300-normal\.woff2\?v=20261031-8/);
});
