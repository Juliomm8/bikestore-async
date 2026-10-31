/* Persistencia validada y sin precios: la fuente de importes siempre es el catálogo del servidor. */
(function attachCartStorage(root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.BikeStoreCartStorage = api;
})(typeof globalThis === 'undefined' ? this : globalThis, function createCartStorage() {
  const MAX_QUANTITY = 99;

  function read(rawValue, catalog) {
    let stored;
    try {
      stored = typeof rawValue === 'string' ? JSON.parse(rawValue) : rawValue;
    } catch {
      return [];
    }
    if (!Array.isArray(stored) || !Array.isArray(catalog)) return [];

    const knownIds = new Set(catalog.map(product => product.id));
    const items = new Map();
    for (const entry of stored) {
      if (!entry || typeof entry !== 'object' || typeof entry.id !== 'string' || !knownIds.has(entry.id)) continue;
      if (!Number.isInteger(entry.quantity) || entry.quantity < 1 || entry.quantity > MAX_QUANTITY) continue;
      const combined = Math.min(MAX_QUANTITY, (items.get(entry.id) || 0) + entry.quantity);
      items.set(entry.id, combined);
    }
    return [...items].map(([id, quantity]) => ({ id, quantity }));
  }

  function serialize(items) {
    if (!Array.isArray(items)) return '[]';
    const safeItems = items.filter(item => item && typeof item.id === 'string'
      && Number.isInteger(item.quantity) && item.quantity >= 1 && item.quantity <= MAX_QUANTITY)
      .map(({ id, quantity }) => ({ id, quantity }));
    return JSON.stringify(safeItems);
  }

  return Object.freeze({ read, serialize });
});
