const products = [
  {
    id: 'sendero-norte',
    name: 'Sendero Norte',
    category: 'montaña',
    price: 920,
    description: 'Para dejar atrás el asfalto y seguir el camino sin prisa.',
    image: '/images/sendero-norte.jpg',
    alt: 'Bicicleta de montaña oscura en un sendero de bosque'
  },
  {
    id: 'ruta-clara',
    name: 'Ruta Clara',
    category: 'ruta',
    price: 1050,
    description: 'Ligera de espíritu, lista para madrugadas y kilómetros nuevos.',
    image: '/images/ruta-clara.jpg',
    alt: 'Bicicleta de ruta blanca apoyada frente a una pared clara'
  },
  {
    id: 'ciudad-verde',
    name: 'Ciudad Verde',
    category: 'urbana',
    price: 640,
    description: 'Una compañera sencilla para recados, cafés y trayectos diarios.',
    image: '/images/ciudad-verde.jpg',
    alt: 'Bicicleta urbana verde junto a una pared de ladrillo'
  },
  {
    id: 'cerro-rojo',
    name: 'Cerro Rojo',
    category: 'montaña',
    price: 1120,
    description: 'Con ganas de subir, bajar y descubrir otra salida el domingo.',
    image: '/images/cerro-rojo.jpg',
    alt: 'Bicicleta de montaña negra y roja junto a un muro exterior',
    imagePosition: 'center 73%'
  },
  {
    id: 'ruta-carmesi',
    name: 'Ruta Carmesí',
    category: 'ruta',
    price: 890,
    description: 'Para quien prefiere el sonido de las ruedas al del tráfico.',
    image: '/images/ruta-carmesi.jpg',
    alt: 'Bicicleta de ruta roja apoyada en una pared de hormigón'
  },
  {
    id: 'ciudad-gris',
    name: 'Ciudad Gris',
    category: 'urbana',
    price: 720,
    description: 'Cómoda para cruzar el barrio y volver por la calle bonita.',
    image: '/images/ciudad-gris.jpg',
    alt: 'Bicicleta urbana gris estacionada en una calle soleada'
  }
];

const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const grid = document.querySelector('#product-grid');
const count = document.querySelector('#catalog-count');
const filters = [...document.querySelectorAll('.filter')];
const form = document.querySelector('#order-form');
const quantity = document.querySelector('#quantity');
const email = document.querySelector('#email');
const total = document.querySelector('#total');
const button = document.querySelector('#submit-button');
const feedback = document.querySelector('#form-feedback');
const orderTitle = document.querySelector('#order-title');
let selected = null;
let activeFilter = 'todas';
let sending = false;

function showFeedback(message, kind = '') {
  feedback.className = `form-feedback${kind ? ` is-${kind}` : ''}`;
  feedback.textContent = message;
}

function renderCatalog() {
  const visible = products.filter(product => activeFilter === 'todas' || product.category === activeFilter);
  grid.replaceChildren(...visible.map(product => {
    const article = document.createElement('article');
    const chosen = selected?.id === product.id;
    article.className = 'product-card';
    article.innerHTML = `
      <div class="product-card__image">
        <img src="${product.image}" alt="${product.alt}" loading="lazy" width="1200" height="850">
      </div>
      <div class="product-card__meta"><span>${product.category}</span><span>Modelo de demostración</span></div>
      <h3>${product.name}</h3>
      <p class="product-card__description">${product.description}</p>
      <div class="product-card__bottom">
        <strong>${money.format(product.price)}</strong>
        <button type="button" data-product="${product.id}" aria-label="${chosen ? `${product.name} seleccionada` : `Elegir ${product.name}`}" aria-pressed="${chosen}" class="${chosen ? 'is-selected' : ''}">${chosen ? 'Elegida' : 'Elegir bicicleta'}</button>
      </div>`;
    if (product.imagePosition) article.querySelector('img').style.objectPosition = product.imagePosition;
    return article;
  }));
  count.textContent = `${visible.length} ${visible.length === 1 ? 'bicicleta' : 'bicicletas'}`;
}

function updateTotal() {
  const units = Number(quantity.value);
  const valid = Number.isInteger(units) && units >= 1;
  quantity.setAttribute('aria-invalid', String(!valid && quantity.value !== ''));
  total.textContent = selected && valid ? money.format(selected.price * units) : '—';
  return valid;
}

function chooseProduct(product) {
  selected = product;
  document.querySelector('#selection-empty').hidden = true;
  document.querySelector('#selection').hidden = false;
  document.querySelector('#selection-image').src = product.image;
  document.querySelector('#selection-image').alt = product.alt;
  document.querySelector('#selection-category').textContent = product.category;
  document.querySelector('#selection-name').textContent = product.name;
  document.querySelector('#selection-price').textContent = `${money.format(product.price)} por unidad`;
  quantity.disabled = false;
  email.disabled = false;
  button.disabled = false;
  quantity.value = '1';
  showFeedback('');
  updateTotal();
  renderCatalog();
  if (window.matchMedia('(max-width: 820px)').matches) {
    document.querySelector('#pedido').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  orderTitle.focus({ preventScroll: true });
}

filters.forEach(filter => filter.addEventListener('click', () => {
  activeFilter = filter.dataset.filter;
  filters.forEach(item => {
    const current = item === filter;
    item.classList.toggle('is-active', current);
    item.setAttribute('aria-pressed', String(current));
  });
  renderCatalog();
}));

grid.addEventListener('click', event => {
  const choice = event.target.closest('button[data-product]');
  if (!choice) return;
  const product = products.find(item => item.id === choice.dataset.product);
  if (product) chooseProduct(product);
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

  const payload = {
    customerEmail: email.value.trim(),
    bikeModel: selected.name,
    quantity: Number(quantity.value),
    unitPrice: selected.price
  };

  sending = true;
  button.disabled = true;
  button.firstChild.textContent = 'Enviando pedido ';
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
    showFeedback(`Pedido recibido: ${order.pedidoId}. El pago se procesa de forma asíncrona; conserva este identificador para seguirlo en los registros.`);
  } catch (error) {
    showFeedback(error instanceof TypeError ? 'No pudimos conectar con la tienda. Comprueba tu conexión e inténtalo otra vez.' : error.message, 'error');
  } finally {
    sending = false;
    button.disabled = false;
    button.firstChild.textContent = 'Confirmar pedido ';
  }
});

renderCatalog();
