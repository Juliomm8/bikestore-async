const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const categoryNames = { gravel: 'Gravel', ruta: 'Ruta', 'montaña': 'Montaña', urbana: 'Ciudad' };
const staticDemo = document.querySelector('meta[name="bikestore-mode"]')?.content === 'static';
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
let reduceMotion = motionPreference.matches;
const themeToggle = document.querySelector('#theme-toggle');
const colorSchemePreference = window.matchMedia('(prefers-color-scheme: dark)');
const assetVersion = window.BIKESTORE_ASSET_VERSION;
const versionedAsset = path => `${path}?v=${encodeURIComponent(assetVersion)}`;

function syncThemeControl() {
  const dark = document.documentElement.dataset.theme === 'dark';
  themeToggle.setAttribute('aria-pressed', String(dark));
  const label = `Cambiar a modo ${dark ? 'claro' : 'oscuro'}`;
  themeToggle.setAttribute('aria-label', label);
  themeToggle.title = label;
  document.querySelector('meta[name="theme-color"]').content = dark ? '#171a18' : '#e9e6de';
}

themeToggle.addEventListener('click', () => {
  const nextTheme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = nextTheme;
  window.BIKESTORE_THEME_PREFERENCE = nextTheme;
  try { localStorage.setItem('bikestore.theme', nextTheme); } catch { /* La preferencia sigue activa mientras esta página permanezca abierta. */ }
  syncThemeControl();
});

colorSchemePreference.addEventListener('change', event => {
  if (window.BIKESTORE_THEME_PREFERENCE !== 'system') return;
  document.documentElement.dataset.theme = event.matches ? 'dark' : 'light';
  syncThemeControl();
});
syncThemeControl();

const $ = selector => document.querySelector(selector);
const grid = $('#product-grid');
const count = $('#catalog-count');
const filters = [...document.querySelectorAll('.filter')];
const form = $('#order-form');
const email = $('#email');
const total = $('#total');
const button = $('#submit-button');
const feedback = $('#form-feedback');
const orderTitle = $('#order-title');
const cartItems = $('#cart-items');
const drawerItems = $('#drawer-items');
const cartDialog = $('#cart-drawer');
const drawerPanel = cartDialog.querySelector('.cart-drawer__panel');
const cartToggle = $('#cart-toggle');
const cartCount = $('#cart-count');
const drawerContinue = $('#cart-continue');
const productDialog = $('#product-dialog');
const quickImage = $('#quick-image');
const quickAdd = $('#quick-add');
const cartStorageKey = 'bikestore.cart.v1';
const cart = new Map();
let drawerClosePending = false;
let focusOrderAfterDrawerClose = false;
let selected = null;
let activeFilter = 'todas';
let sending = false;

if (staticDemo) {
  $('#static-demo-banner').hidden = false;
  $('#static-order-note').hidden = false;
  form.hidden = true;
  $('.cart-intro').textContent = 'Explora la selección y ajusta las cantidades; el total mostrado es solo una estimación.';
  $('#cart-drawer-description').textContent = 'Tu selección se guarda en este navegador para esta demostración visual.';
  $('#drawer-price-note').textContent = 'Total estimado del catálogo. No se envía ningún pedido ni se procesa un pago.';
  drawerContinue.textContent = 'Ver resumen del carrito';
}

const findProduct = id => products.find(product => product.id === id);

function showFeedback(message, kind = '') {
  feedback.className = `form-feedback${kind ? ` is-${kind}` : ''}`;
  feedback.textContent = message;
}

/* ---------- Catálogo ---------- */

function cardHtml(product) {
  const quantity = cart.get(product.id)?.quantity ?? 0;
  return `
    <button class="card__media card__quick-view" type="button" data-quick-view="${product.id}" aria-label="Ver detalles de ${product.name}" style="--image-fit:${product.imageFit};--image-position:${product.imagePosition};--image-scale:${product.imageScale};--image-background:${product.imageBackground}">
      <img src="${versionedAsset(product.image)}" alt="" loading="lazy" width="${product.imageWidth}" height="${product.imageHeight}">
      <span class="quick-view-hint" aria-hidden="true">Ver bicicleta <span>↗</span></span>
    </button>
    <div class="card__body">
      <div class="card__title">
        <h3>${product.name}</h3>
        <span class="card__price">${money.format(product.price)}</span>
      </div>
      <p class="card__category">${categoryNames[product.category]} · ${product.specs[0]}</p>
      <p class="card__desc">${product.description}</p>
      <p class="card__specs">${product.specs.slice(1).join(' · ')}</p>
      <button type="button" class="link-button${quantity ? ' is-selected' : ''}" data-product="${product.id}" aria-label="${quantity ? `Añadir otra ${product.name} al carrito; cantidad actual ${quantity}` : `Añadir ${product.name} al carrito`}"${quantity >= 99 || sending ? ' disabled' : ''}>${quantity ? `En carrito · ${quantity}` : 'Añadir al carrito'}</button>
    </div>`;
}

function renderCatalog() {
  const visible = products.filter(product => activeFilter === 'todas' || product.category === activeFilter);
  grid.replaceChildren(...visible.map(product => {
    const article = document.createElement('article');
    article.className = `card${cart.has(product.id) ? ' is-selected' : ''}`;
    article.dataset.id = product.id;
    article.innerHTML = cardHtml(product);
    return article;
  }));
  count.textContent = `${visible.length} ${visible.length === 1 ? 'bicicleta' : 'bicicletas'}`;
}

let quickViewReturnFocus = null;
function openQuickView(product, trigger) {
  if (!product) return;
  quickViewReturnFocus = trigger;
  quickImage.src = versionedAsset(product.image);
  quickImage.alt = product.alt;
  quickImage.width = product.imageWidth;
  quickImage.height = product.imageHeight;
  quickImage.style.objectFit = product.imageFit;
  quickImage.style.objectPosition = product.imagePosition;
  $('#quick-media').style.backgroundColor = product.imageBackground;
  $('#quick-media').style.setProperty('--quick-image-scale', product.imageScale);
  $('#quick-category').textContent = `${categoryNames[product.category]} · ${product.specs[0]}`;
  $('#quick-title').textContent = product.name;
  $('#quick-price').textContent = money.format(product.price);
  $('#quick-description').textContent = product.description;
  $('#quick-specs').replaceChildren(...product.specs.map(spec => {
    const detail = document.createElement('li');
    detail.textContent = spec;
    return detail;
  }));
  quickAdd.dataset.product = product.id;
  productDialog.showModal();
  document.body.classList.add('dialog-open');
}

function syncDialogBodyLock() {
  document.body.classList.toggle('dialog-open', cartDialog.open || productDialog.open);
}

function containDialogTab(event, dialog) {
  if (event.key !== 'Tab' || !dialog?.open) return;
  const controls = [...dialog.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')]
    .filter(control => control.getClientRects().length > 0 && !control.closest('[hidden]'));
  if (!controls.length) {
    event.preventDefault();
    dialog.focus();
    return;
  }
  const first = controls[0];
  const last = controls.at(-1);
  if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (document.activeElement === last || !dialog.contains(document.activeElement))) {
    event.preventDefault();
    first.focus();
  }
}

document.addEventListener('keydown', event => {
  containDialogTab(event, document.querySelector('dialog[open]'));
});

function updateProductButtons() {
  grid.querySelectorAll('.card').forEach(card => {
    const product = findProduct(card.dataset.id);
    const quantity = cart.get(product.id)?.quantity ?? 0;
    const add = card.querySelector('button[data-product]');
    card.classList.toggle('is-selected', quantity > 0);
    add.classList.toggle('is-selected', quantity > 0);
    add.textContent = quantity ? `En carrito · ${quantity}` : 'Añadir al carrito';
    add.setAttribute('aria-label', quantity
      ? `Añadir otra ${product.name} al carrito; cantidad actual ${quantity}`
      : `Añadir ${product.name} al carrito`);
    add.disabled = quantity >= 99 || sending;
  });
}

function setFilter(filter) {
  if (filter.dataset.filter === activeFilter) return;
  activeFilter = filter.dataset.filter;
  filters.forEach(item => {
    const current = item === filter;
    item.classList.toggle('is-active', current);
    item.setAttribute('aria-pressed', String(current));
  });
  if (reduceMotion) { renderCatalog(); return; }
  grid.classList.add('is-changing');
  setTimeout(() => {
    renderCatalog();
    grid.classList.remove('is-changing');
  }, 220);
}

filters.forEach(filter => filter.addEventListener('click', () => setFilter(filter)));

grid.addEventListener('click', event => {
  const quickView = event.target.closest('button[data-quick-view]');
  if (quickView) {
    openQuickView(findProduct(quickView.dataset.quickView), quickView);
    return;
  }
  const choice = event.target.closest('button[data-product]');
  if (choice) addToCart(findProduct(choice.dataset.product));
});

/* ---------- Carrito y pedido ---------- */

function cartTotal() {
  return [...cart.values()].reduce((sum, item) => sum + item.product.price * item.quantity, 0);
}

function persistCart() {
  const minimalItems = [...cart].map(([id, item]) => ({ id, quantity: item.quantity }));
  try { localStorage.setItem(cartStorageKey, window.BikeStoreCartStorage.serialize(minimalItems)); } catch { /* El pedido sigue disponible durante esta sesión si el almacenamiento está bloqueado. */ }
}

function restoreCart() {
  try {
    const savedItems = window.BikeStoreCartStorage.read(localStorage.getItem(cartStorageKey), products);
    savedItems.forEach(({ id, quantity }) => {
      const product = findProduct(id);
      if (product) cart.set(id, { product, quantity });
    });
    persistCart();
  } catch { /* Un almacenamiento no disponible deja el carrito vacío sin bloquear la tienda. */ }
}

function updateCartTotals(sourceInput = null) {
  if (sourceInput) {
    const quantity = Number(sourceInput.value);
    const lineValid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 99;
    document.querySelectorAll(`[data-cart-quantity="${sourceInput.dataset.cartQuantity}"]`).forEach(input => {
      if (input !== sourceInput) input.value = sourceInput.value;
      input.setAttribute('aria-invalid', String(!lineValid));
    });
    if (lineValid) {
      const item = cart.get(sourceInput.dataset.cartQuantity);
      if (item) item.quantity = quantity;
      persistCart();
    }
  }

  const inputs = [...document.querySelectorAll('[data-cart-quantity]')];
  const valid = cart.size > 0 && inputs.every(input => {
    const quantity = Number(input.value);
    const lineValid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 99;
    input.setAttribute('aria-invalid', String(!lineValid));
    return lineValid;
  });
  const subtotal = cartTotal();
  const unitCount = [...cart.values()].reduce((sum, item) => sum + item.quantity, 0);

  document.querySelectorAll('[data-line-total]').forEach(line => {
    const row = line.closest('.cart-item');
    const product = findProduct(row.dataset.id);
    const invalid = row.querySelector('[data-cart-quantity][aria-invalid="true"]');
    line.textContent = invalid ? '—' : money.format(product.price * cart.get(product.id).quantity);
  });

  total.textContent = valid ? money.format(subtotal) : '—';
  $('#drawer-total').textContent = valid ? money.format(subtotal) : '—';
  button.disabled = !valid || sending;
  drawerContinue.disabled = !valid || sending;
  drawerContinue.classList.toggle('is-ready', valid && !sending);
  document.querySelectorAll('.cart-items input, .cart-items button').forEach(control => { control.disabled = sending; });

  const previousCount = Number(cartCount.textContent) || 0;
  cartCount.textContent = String(unitCount);
  cartCount.hidden = unitCount === 0;
  cartToggle.setAttribute('aria-label', `Abrir la bolsa, ${unitCount} ${unitCount === 1 ? 'unidad' : 'unidades'}`);
  if (unitCount > previousCount && !reduceMotion) {
    cartCount.classList.remove('is-bumped');
    requestAnimationFrame(() => cartCount.classList.add('is-bumped'));
  }
  $('#drawer-units').textContent = `${unitCount} ${unitCount === 1 ? 'unidad' : 'unidades'} · ${cart.size} ${cart.size === 1 ? 'modelo' : 'modelos'}`;

  return valid;
}

function updateOrderPreview(product) {
  const image = $('#order-image');
  if (!product) {
    image.hidden = true;
    image.removeAttribute('data-product-id');
    $('#order-placeholder').hidden = false;
    return;
  }
  if (image.dataset.productId === product.id) return;

  image.hidden = false;
  $('#order-placeholder').hidden = true;
  image.classList.remove('is-in');
  image.src = versionedAsset(product.image);
  image.alt = product.alt;
  image.dataset.productId = product.id;
  image.style.objectFit = product.imageFit;
  image.style.objectPosition = product.imagePosition;
  image.style.setProperty('--image-scale', product.imageScale);
  $('#order-figure').style.backgroundColor = product.imageBackground;
  requestAnimationFrame(() => requestAnimationFrame(() => image.classList.add('is-in')));
}

function cartItemRow({ product, quantity }, surface) {
  const inDrawer = surface === 'drawer';
  const quantityId = `quantity-${surface}-${product.id}`;
  const row = document.createElement('article');
  row.className = `cart-item${inDrawer ? ' cart-item--drawer' : ''}`;
  row.dataset.id = product.id;
  row.innerHTML = `
    ${inDrawer ? `<img class="cart-item__thumb" src="${versionedAsset(product.image)}" alt="" style="--cart-image-background:${product.imageBackground}">` : ''}
    <div class="cart-item__main">
      <div class="cart-item__info">
        <h3>${product.name}</h3>
        <p>${money.format(product.price)} por unidad</p>
        <strong data-line-total>${money.format(product.price * quantity)}</strong>
      </div>
      <button class="cart-item__remove" type="button" data-remove="${product.id}" aria-label="Quitar ${product.name} del carrito">
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m6 6 12 12M18 6 6 18"></path></svg>
      </button>
    </div>
    <div class="cart-item__controls">
      <label for="${quantityId}">Cantidad</label>
      <input id="${quantityId}" type="number" min="1" max="99" step="1" inputmode="numeric" value="${quantity}" data-cart-quantity="${product.id}" aria-label="Cantidad de ${product.name}">
    </div>`;
  return row;
}

function renderCart(announcement = '') {
  const entries = [...cart.values()];
  cartItems.replaceChildren(...entries.map(item => cartItemRow(item, 'order')));
  drawerItems.replaceChildren(...entries.map(item => cartItemRow(item, 'drawer')));

  const hasItems = entries.length > 0;
  $('#cart-empty').hidden = hasItems;
  $('#drawer-empty').hidden = hasItems;
  updateOrderPreview(selected && cart.has(selected.id) ? selected : entries.at(-1)?.product);
  if (announcement) $('#cart-status').textContent = announcement;
  updateCartTotals();
  updateProductButtons();
}

function addToCart(product) {
  if (!product || sending) return;
  const entry = cart.get(product.id);
  if (entry && entry.quantity >= 99) {
    showFeedback('Puedes agregar hasta 99 unidades de cada modelo.', 'error');
    return;
  }
  cart.set(product.id, { product, quantity: (entry?.quantity ?? 0) + 1 });
  persistCart();
  selected = product;
  renderCart(`${product.name} se agregó al carrito.`);
  showFeedback('');
}

function handleCartInput(event) {
  const input = event.target.closest('[data-cart-quantity]');
  if (!input) return;
  const quantity = Number(input.value);
  const valid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 99;
  updateCartTotals(input);
  if (valid && feedback.classList.contains('is-error')) showFeedback('');
  if (!valid) showFeedback('Cada modelo debe tener una cantidad entera entre 1 y 99.', 'error');
}

function handleCartClick(event) {
  const remove = event.target.closest('[data-remove]');
  if (!remove) return;
  const surface = remove.closest('.cart-items');
  const product = findProduct(remove.dataset.remove);
  cart.delete(product.id);
  persistCart();
  if (selected?.id === product.id) selected = [...cart.values()].at(-1)?.product ?? null;
  renderCart(`${product.name} se quitó del carrito.`);
  showFeedback('');
  (surface.querySelector('[data-remove]') || (surface === drawerItems ? $('#cart-drawer-close') : orderTitle)).focus();
}

[cartItems, drawerItems].forEach(container => {
  container.addEventListener('input', handleCartInput);
  container.addEventListener('click', handleCartClick);
});

quickAdd.addEventListener('click', () => {
  const product = findProduct(quickAdd.dataset.product);
  if (cart.get(product.id)?.quantity >= 99) {
    addToCart(product);
    return;
  }
  addToCart(product);
  productDialog.close();
});
$('#quick-close').addEventListener('click', () => productDialog.close());
productDialog.addEventListener('click', event => { if (event.target === productDialog) productDialog.close(); });
productDialog.addEventListener('close', () => {
  syncDialogBodyLock();
  if (quickViewReturnFocus?.isConnected) quickViewReturnFocus.focus({ preventScroll: true });
});

document.querySelectorAll('[data-choose]').forEach(control => {
  control.addEventListener('click', () => addToCart(findProduct(control.dataset.choose)));
});

document.querySelectorAll('[data-price]').forEach(node => {
  node.textContent = money.format(findProduct(node.dataset.price).price);
});

// Los enlaces de las piezas destacadas llevan a la ficha; no alteran el carrito.
document.querySelectorAll('[data-pick]').forEach(link => {
  link.addEventListener('click', () => {
    if (activeFilter !== 'todas') setFilter(filters[0]);
    setTimeout(() => {
      const card = grid.querySelector(`[data-id="${link.dataset.pick}"]`);
      if (!card) return;
      card.classList.add('is-flagged');
      setTimeout(() => card.classList.remove('is-flagged'), 1800);
      card.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' });
    }, activeFilter === 'todas' ? 0 : 300);
  });
});

email.addEventListener('input', () => {
  email.removeAttribute('aria-invalid');
  if (feedback.classList.contains('is-error')) showFeedback('');
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (staticDemo) return;
  if (sending) return;
  if (!cart.size) {
    showFeedback('Añade al menos una bicicleta antes de confirmar el pedido.', 'error');
    return;
  }
  if (!updateCartTotals()) {
    showFeedback('Revisa las cantidades del carrito antes de continuar.', 'error');
    cartItems.querySelector('[aria-invalid="true"]')?.focus();
    return;
  }
  if (!email.validity.valid || !email.value.trim()) {
    email.setAttribute('aria-invalid', 'true');
    showFeedback('Ingresa un correo electrónico válido.', 'error');
    email.focus();
    return;
  }

  const payload = {
    customerEmail: email.value.trim(),
    items: [...cart.values()].map(item => ({
      bikeModel: item.product.name,
      quantity: item.quantity
    }))
  };

  sending = true;
  updateCartTotals();
  updateProductButtons();
  email.disabled = true;
  button.classList.add('is-busy');
  button.textContent = 'Enviando pedido';
  showFeedback('Registrando tu pedido…', 'loading');
  try {
    const response = await fetch('/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      if (response.status === 400) throw new Error('Revisa el correo, los modelos y las cantidades del carrito.');
      throw new Error('El pedido no pudo registrarse. Inténtalo de nuevo en unos momentos.');
    }
    const order = await response.json();
    if (!order.pedidoId) throw new Error('No recibimos un identificador del pedido. Revisa los registros antes de repetirlo.');
    showConfirmation(order.pedidoId, payload, order.total);
  } catch (error) {
    showFeedback(error instanceof TypeError ? 'No pudimos conectar con la tienda. Comprueba tu conexión e inténtalo otra vez.' : error.message, 'error');
  } finally {
    sending = false;
    updateCartTotals();
    updateProductButtons();
    email.disabled = false;
    button.classList.remove('is-busy');
    button.textContent = 'Confirmar pedido';
  }
});

function showConfirmation(id, payload, serverTotal) {
  const panel = $('#confirmation');
  $('#confirmation-id').textContent = id;
  const items = payload.items.map(item => `${item.quantity} × ${item.bikeModel}`).join(' · ');
  const displayTotal = serverTotal ?? cartTotal();
  $('#confirmation-detail').textContent = `${items} · ${money.format(Number(displayTotal))} · ${payload.customerEmail}`;
  showFeedback('');
  cart.clear();
  selected = null;
  persistCart();
  renderCart();
  $('#order-flow').hidden = true;
  panel.hidden = false;
  panel.classList.remove('is-in');
  requestAnimationFrame(() => requestAnimationFrame(() => panel.classList.add('is-in')));
  panel.focus({ preventScroll: true });
  panel.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' });
}

function showOrderForm() {
  $('#confirmation').hidden = true;
  $('#order-flow').hidden = false;
}

$('#new-order').addEventListener('click', () => {
  email.value = '';
  cart.clear();
  selected = null;
  persistCart();
  showOrderForm();
  renderCart('El carrito está listo para un nuevo pedido.');
  showFeedback('');
  email.focus();
});

/* ---------- Navegación, encabezado y revelados ---------- */

const header = $('#site-header');
const toggle = $('#menu-toggle');
const nav = $('#site-nav');

function setMenu(open) {
  toggle.setAttribute('aria-expanded', String(open));
  toggle.querySelector('.menu-toggle__label').textContent = open ? 'Cerrar' : 'Menú';
  header.classList.toggle('is-open', open);
  document.body.classList.toggle('menu-open', open);
}
toggle.addEventListener('click', () => setMenu(toggle.getAttribute('aria-expanded') !== 'true'));
nav.addEventListener('click', event => { if (event.target.closest('a')) setMenu(false); });
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') {
    setMenu(false);
    toggle.focus();
  }
});

let drawerReturnFocus = cartToggle;
function openCartDrawer() {
  if (cartDialog.open) return;
  setMenu(false);
  drawerClosePending = false;
  cartDialog.classList.remove('is-closing');
  drawerReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : cartToggle;
  cartDialog.showModal();
  cartToggle.setAttribute('aria-expanded', 'true');
  syncDialogBodyLock();
  requestAnimationFrame(() => $('#cart-drawer-close').focus({ preventScroll: true }));
}

function finishCartDrawerClose() {
  if (!drawerClosePending || !cartDialog.open) return;
  drawerClosePending = false;
  cartDialog.classList.remove('is-closing');
  cartDialog.close();
}

drawerPanel.addEventListener('transitionend', event => {
  if (event.target === drawerPanel && event.propertyName === 'transform') finishCartDrawerClose();
});

function closeCartDrawer() {
  if (!cartDialog.open || drawerClosePending) return;
  if (reduceMotion) {
    cartDialog.close();
    return;
  }
  drawerClosePending = true;
  cartDialog.classList.add('is-closing');
}

motionPreference.addEventListener('change', event => {
  reduceMotion = event.matches;
  if (reduceMotion) finishCartDrawerClose();
});

cartToggle.addEventListener('click', openCartDrawer);
$('#cart-drawer-close').addEventListener('click', closeCartDrawer);
cartDialog.addEventListener('click', event => { if (event.target === cartDialog) closeCartDrawer(); });
cartDialog.addEventListener('cancel', event => {
  event.preventDefault();
  closeCartDrawer();
});
cartDialog.addEventListener('close', () => {
  syncDialogBodyLock();
  cartToggle.setAttribute('aria-expanded', 'false');
  if (focusOrderAfterDrawerClose) {
    focusOrderAfterDrawerClose = false;
    window.location.hash = 'pedido';
    email.focus({ preventScroll: true });
  } else if (drawerReturnFocus?.isConnected) {
    drawerReturnFocus.focus({ preventScroll: true });
  }
});
$('#drawer-explore').addEventListener('click', closeCartDrawer);
drawerContinue.addEventListener('click', () => {
  focusOrderAfterDrawerClose = true;
  closeCartDrawer();
});

const hero = $('#inicio');
new IntersectionObserver(([entry]) => {
  header.classList.toggle('is-solid', !entry.isIntersecting);
}, { rootMargin: '-72px 0px 0px 0px' }).observe(hero);

const revealItems = document.querySelectorAll('[data-reveal]');
if (reduceMotion || !('IntersectionObserver' in window)) {
  revealItems.forEach(item => item.classList.add('is-visible'));
} else {
  const observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('is-visible');
      observer.unobserve(entry.target);
    }
  }), { threshold: 0.2 });
  revealItems.forEach(item => observer.observe(item));
}

restoreCart();
renderCatalog();
renderCart();
