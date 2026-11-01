import { access, cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const staticSource = path.join(projectRoot, 'src/main/resources/static');
const defaultOutput = path.join(projectRoot, '_site');

function replaceExactlyOnce(source, pattern, replacement, description) {
  const matches = [...source.matchAll(pattern)];
  if (matches.length !== 1) {
    throw new Error(`No se pudo preparar Pages: se esperaba una referencia de ${description}, se encontraron ${matches.length}.`);
  }
  return source.replace(pattern, replacement);
}

export async function preparePagesSite({ sourceDir = staticSource, outputDir = defaultOutput } = {}) {
  const source = path.resolve(sourceDir);
  const output = path.resolve(outputDir);
  if (output === source || output.startsWith(`${source}${path.sep}`)) {
    throw new Error('El directorio de publicación no puede estar dentro del sitio estático de origen.');
  }
  try {
    await access(output);
    throw new Error(`El directorio de publicación ya existe: ${output}`);
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }

  await mkdir(output, { recursive: true });
  await cp(source, output, { recursive: true });

  const indexPath = path.join(output, 'index.html');
  let html = await readFile(indexPath, 'utf8');
  html = replaceExactlyOnce(html, /<meta name="bikestore-mode" content="server">/g,
    '<meta name="bikestore-mode" content="static">', 'el modo de ejecución');
  html = replaceExactlyOnce(html, /(<aside class="demo-banner" id="static-demo-banner") hidden/g,
    '$1', 'el aviso estático');
  html = replaceExactlyOnce(html, /(<div class="static-order-note" id="static-order-note") hidden/g,
    '$1', 'la nota del pedido');
  html = replaceExactlyOnce(html, /(<form id="order-form" novalidate)>/g,
    '$1 hidden>', 'el formulario local');
  await writeFile(indexPath, html, 'utf8');
  await writeFile(path.join(output, '.nojekyll'), '', 'utf8');

  return { outputDir: output, mode: 'static', sourceDir: source };
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  const destination = process.argv[2] ? path.resolve(process.argv[2]) : defaultOutput;
  const result = await preparePagesSite({ outputDir: destination });
  console.log(`Sitio GitHub Pages preparado en ${result.outputDir}`);
}
