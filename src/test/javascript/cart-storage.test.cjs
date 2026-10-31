const assert = require('node:assert/strict');
const test = require('node:test');
const { read, serialize } = require('../../main/resources/static/js/cart-storage.js');

const catalog = [
  { id: 'alerce', price: 1560 },
  { id: 'ruta-clara', price: 1050 }
];

test('recupera solamente identificadores y cantidades de modelos actuales', () => {
  const restored = read(JSON.stringify([
    { id: 'alerce', quantity: 2, price: 0.01 },
    { id: 'desconocida', quantity: 1 },
    { id: 'ruta-clara', quantity: 3 }
  ]), catalog);

  assert.deepEqual(restored, [
    { id: 'alerce', quantity: 2 },
    { id: 'ruta-clara', quantity: 3 }
  ]);
  assert.equal('price' in restored[0], false);
});

test('descarta cantidades inválidas y normaliza duplicados hasta el límite', () => {
  const restored = read([
    { id: 'alerce', quantity: 70 },
    { id: 'alerce', quantity: 40 },
    { id: 'ruta-clara', quantity: 1.5 },
    { id: 'ruta-clara', quantity: 0 },
    { id: 'ruta-clara', quantity: 100 }
  ], catalog);

  assert.deepEqual(restored, [{ id: 'alerce', quantity: 99 }]);
});

test('una preferencia corrupta o una estructura inesperada produce un carrito vacío', () => {
  assert.deepEqual(read('{ esto no es JSON', catalog), []);
  assert.deepEqual(read('{"id":"alerce"}', catalog), []);
  assert.deepEqual(read(null, catalog), []);
});

test('serializa el mínimo necesario para recuperar el carrito', () => {
  const serialized = serialize([
    { id: 'alerce', quantity: 2, product: { price: 1560 } },
    { id: 'ruta-clara', quantity: 1 }
  ]);

  assert.equal(serialized, '[{"id":"alerce","quantity":2},{"id":"ruta-clara","quantity":1}]');
});
