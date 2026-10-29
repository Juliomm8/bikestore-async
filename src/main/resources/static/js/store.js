const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const categoryNames = { gravel: 'Gravel', ruta: 'Ruta', 'montaña': 'Montaña', urbana: 'Ciudad' };
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const $ = selector => document.querySelector(selector);
const grid = $('#product-grid');
const count = $('#catalog-count');
const filters = [...document.querySelectorAll('.filter')];
const form = $('#order-form');
const quantity = $('#quantity');
const email = $('#email');
const total = $('#total');
const button = $('#submit-button');
const feedback = $('#form-feedback');
const orderTitle = $('#order-title');
const pickbar = $('#pickbar');
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
  const chosen = selected?.id === product.id;
  return `
    <div class="card__media">
      <img src="${product.image}" alt="${product.alt}" loading="lazy" width="1600" height="1200" style="object-position:${product.focus}">
    </div>
    <div class="card__body">
      <div class="card__title">
        <h3>${product.name}</h3>
        <span class="card__price">${money.format(product.price)}</span>
      </div>
      <p class="card__category">${categoryNames[product.category]} · ${product.specs[0]}</p>
      <p class="card__desc">${product.description}</p>
      <p class="card__specs">${product.specs.slice(1).join(' · ')}</p>
      <button type="button" class="link-button${chosen ? ' is-selected' : ''}" data-product="${product.id}" aria-pressed="${chosen}" aria-label="${chosen ? `${product.name}, elegida` : `Elegir ${product.name}`}">${chosen ? 'Elegida' : 'Elegir'}</button>
    </div>`;
}

function renderCatalog() {
  const visible = products.filter(product => activeFilter === 'todas' || product.category === activeFilter);
  grid.replaceChildren(...visible.map(product => {
    const article = document.createElement('article');
    article.className = `card${selected?.id === product.id ? ' is-selected' : ''}`;
    article.dataset.id = product.id;
    article.innerHTML = cardHtml(product);
    return article;
  }));
  count.textContent = `${visible.length} ${visible.length === 1 ? 'bicicleta' : 'bicicletas'}`;
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
  if (choice) chooseProduct(findProduct(choice.dataset.product));
});

/* ---------- Selección y pedido ---------- */

function updateTotal() {
  const units = Number(quantity.value);
  const valid = Number.isInteger(units) && units >= 1;
  quantity.setAttribute('aria-invalid', String(!valid && quantity.value !== ''));
  total.textContent = selected && valid ? money.format(selected.price * units) : '—';
  return valid;
}

function chooseProduct(product, { scroll = false } = {}) {
  if (!product) return;
  const changed = selected?.id !== product.id;
  selected = product;

  const image = $('#order-image');
  if (changed) {
    image.hidden = false;
    $('#order-placeholder').hidden = true;
    image.classList.remove('is-in');
    image.src = product.image;
    image.alt = product.alt;
    image.style.objectPosition = product.focus;
    requestAnimationFrame(() => requestAnimationFrame(() => image.classList.add('is-in')));
  }
  $('#sum-model').textContent = `${product.name} · ${categoryNames[product.category]}`;
  $('#sum-price').textContent = money.format(product.price);
  quantity.disabled = false;
  email.disabled = false;
  button.disabled = false;
  if (changed) quantity.value = '1';
  showFeedback('');
  updateTotal();
  showOrderForm();

  pickbar.hidden = false;
  $('#pickbar-name').textContent = product.name;
  $('#pickbar-price').textContent = money.format(product.price);

  // Actualiza solo la ficha afectada para no reiniciar el scroll ni el foco del catálogo.
  grid.querySelectorAll('.card').forEach(card => {
    const chosen = card.dataset.id === product.id;
    card.classList.toggle('is-selected', chosen);
    const choose = card.querySelector('button[data-product]');
    choose.classList.toggle('is-selected', chosen);
    choose.textContent = chosen ? 'Elegida' : 'Elegir';
    choose.setAttribute('aria-pressed', String(chosen));
    const name = findProduct(card.dataset.id).name;
    choose.setAttribute('aria-label', chosen ? `${name}, elegida` : `Elegir ${name}`);
  });

  if (scroll) {
    $('#pedido').scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    orderTitle.focus({ preventScroll: true });
  }
}

function showOrderForm() {
  $('#confirmation').hidden = true;
  $('#order-flow').hidden = false;
}

document.querySelectorAll('[data-choose]').forEach(control => {
  control.addEventListener('click', () => chooseProduct(findProduct(control.dataset.choose)));
});

document.querySelectorAll('[data-price]').forEach(node => {
  node.textContent = money.format(findProduct(node.dataset.price).price);
});

// Un enlace de las piezas destacadas lleva al catálogo y resalta la ficha.
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

quantity.addEventListener('input', () => {
  updateTotal();
  if (feedback.classList.contains('is-error')) showFeedback('');
});
email.addEventListener('input', () => {
  email.removeAttribute('aria-invalid');
  if (feedback.classList.contains('is-error')) showFeedback('');
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (sending) return;
  if (!selected) {
    showFeedback('Elige una bicicleta del catálogo para continuar.', 'error');
    return;
  }
  if (!updateTotal()) {
    showFeedback('Ingresa una cantidad entera de al menos 1.', 'error');
    quantity.focus();
    return;
  }
  if (!email.validity.valid || !email.value.trim()) {
    email.setAttribute('aria-invalid', 'true');
    showFeedback('Ingresa un correo electrónico válido.', 'error');
    email.focus();
    return;
  }

  // Contrato del API: customerEmail, bikeModel, quantity, unitPrice.
  const payload = {
    customerEmail: email.value.trim(),
    bikeModel: selected.name,
    quantity: Number(quantity.value),
    unitPrice: selected.price
  };

  sending = true;
  button.disabled = true;
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
      if (response.status === 400) throw new Error('Revisa el correo y la cantidad antes de intentar de nuevo.');
      throw new Error('El pedido no pudo registrarse. Inténtalo de nuevo en unos momentos.');
    }
    const order = await response.json();
    if (!order.pedidoId) throw new Error('No recibimos un identificador del pedido. Revisa los registros antes de repetirlo.');
    showConfirmation(order.pedidoId, payload);
  } catch (error) {
    showFeedback(error instanceof TypeError ? 'No pudimos conectar con la tienda. Comprueba tu conexión e inténtalo otra vez.' : error.message, 'error');
  } finally {
    sending = false;
    button.disabled = !selected;
    button.classList.remove('is-busy');
    button.textContent = 'Confirmar pedido';
  }
});

function showConfirmation(id, payload) {
  const panel = $('#confirmation');
  $('#confirmation-id').textContent = id;
  $('#confirmation-detail').textContent =
    `${payload.quantity} × ${payload.bikeModel} · ${money.format(payload.quantity * payload.unitPrice)} · ${payload.customerEmail}`;
  showFeedback('');
  $('#order-flow').hidden = true;
  panel.hidden = false;
  panel.classList.remove('is-in');
  requestAnimationFrame(() => requestAnimationFrame(() => panel.classList.add('is-in')));
  panel.focus({ preventScroll: true });
  panel.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' });
}

$('#new-order').addEventListener('click', () => {
  email.value = '';
  quantity.value = '1';
  updateTotal();
  showOrderForm();
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

// La barra de selección se oculta cuando el pedido ya está a la vista.
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
