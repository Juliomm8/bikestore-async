const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const categoryNames = { gravel: 'Gravel', ruta: 'Ruta', 'montaña': 'Montaña', urbana: 'Ciudad' };
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const assetVersion = window.BIKESTORE_ASSET_VERSION;
const versionedAsset = path => `${path}?v=${encodeURIComponent(assetVersion)}`;

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
const pickbar = $('#pickbar');
const cart = new Map();
let selected = null;
let activeFilter = 'todas';
let sending = false;

const findProduct = id => products.find(product => product.id === id);

function showFeedback(message, kind = '') {
  feedback.className = `form-feedback${kind ? ` is-${kind}` : ''}`;
  feedback.textContent = message;
}

/* ---------- Catálogo ---------- */

function cardHtml(product) {
  const quantity = cart.get(product.id)?.quantity ?? 0;
  return `
    <div class="card__media" style="--image-fit:${product.imageFit};--image-position:${product.imagePosition};--image-scale:${product.imageScale};--image-background:${product.imageBackground}">
      <img src="${versionedAsset(product.image)}" alt="${product.alt}" loading="lazy" width="${product.imageWidth}" height="${product.imageHeight}">
    </div>
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
  const choice = event.target.closest('button[data-product]');
  if (choice) addToCart(findProduct(choice.dataset.product));
});

/* ---------- Carrito y pedido ---------- */

function cartTotal() {
  return [...cart.values()].reduce((sum, item) => sum + item.product.price * item.quantity, 0);
}

function updateCartTotals() {
  let valid = cart.size > 0;
  let subtotal = 0;
  let unitCount = 0;

  cartItems.querySelectorAll('[data-cart-quantity]').forEach(input => {
    const product = findProduct(input.dataset.cartQuantity);
    const quantity = Number(input.value);
    const lineTotal = input.closest('.cart-item').querySelector('[data-line-total]');
    const lineValid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 99;
    input.setAttribute('aria-invalid', String(!lineValid));
    if (lineValid) {
      cart.get(product.id).quantity = quantity;
      const lineAmount = product.price * quantity;
      lineTotal.textContent = money.format(lineAmount);
      subtotal += lineAmount;
      unitCount += quantity;
    } else {
      lineTotal.textContent = '—';
      valid = false;
    }
  });

  total.textContent = valid ? money.format(subtotal) : '—';
  button.disabled = !valid || sending;
  cartItems.querySelectorAll('input, button').forEach(control => { control.disabled = sending; });
  if (cart.size) {
    const modelCount = cart.size;
    $('#pickbar-name').textContent = `${modelCount} ${modelCount === 1 ? 'modelo' : 'modelos'} · ${unitCount} ${unitCount === 1 ? 'bicicleta' : 'bicicletas'}`;
    $('#pickbar-price').textContent = valid ? money.format(subtotal) : 'Revisa las cantidades';
  }
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

function renderCart(announcement = '') {
  const entries = [...cart.values()];
  cartItems.replaceChildren(...entries.map(({ product, quantity }) => {
    const row = document.createElement('article');
    row.className = 'cart-item';
    row.dataset.id = product.id;
    row.innerHTML = `
      <div class="cart-item__info">
        <h3>${product.name}</h3>
        <p>${money.format(product.price)} por unidad</p>
        <strong data-line-total>${money.format(product.price * quantity)}</strong>
      </div>
      <div class="cart-item__controls">
        <label for="quantity-${product.id}">Cantidad</label>
        <input id="quantity-${product.id}" type="number" min="1" max="99" step="1" inputmode="numeric" value="${quantity}" data-cart-quantity="${product.id}" aria-label="Cantidad de ${product.name}">
      </div>
      <button class="cart-item__remove" type="button" data-remove="${product.id}" aria-label="Quitar ${product.name} del carrito">Quitar</button>`;
    return row;
  }));

  const hasItems = entries.length > 0;
  $('#cart-empty').hidden = hasItems;
  pickbar.hidden = !hasItems;
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
  selected = product;
  renderCart(`${product.name} se agregó al carrito.`);
  showFeedback('');
}

cartItems.addEventListener('input', event => {
  const input = event.target.closest('[data-cart-quantity]');
  if (!input) return;
  const quantity = Number(input.value);
  const valid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 99;
  updateCartTotals();
  if (valid && feedback.classList.contains('is-error')) showFeedback('');
  if (!valid) {
    showFeedback('Cada modelo debe tener una cantidad entera entre 1 y 99.', 'error');
  }
});

cartItems.addEventListener('click', event => {
  const remove = event.target.closest('[data-remove]');
  if (!remove) return;
  const product = findProduct(remove.dataset.remove);
  cart.delete(product.id);
  if (selected?.id === product.id) selected = [...cart.values()].at(-1)?.product ?? null;
  renderCart(`${product.name} se quitó del carrito.`);
  showFeedback('');
  (cartItems.querySelector('[data-remove]') || orderTitle).focus();
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

const hero = $('#inicio');
new IntersectionObserver(([entry]) => {
  header.classList.toggle('is-solid', !entry.isIntersecting);
}, { rootMargin: '-72px 0px 0px 0px' }).observe(hero);

new IntersectionObserver(([entry]) => {
  pickbar.classList.toggle('is-away', entry.isIntersecting);
}, { threshold: 0.25 }).observe($('#pedido'));

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

renderCatalog();
renderCart();
