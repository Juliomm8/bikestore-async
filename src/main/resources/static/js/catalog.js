// Catálogo de demostración: modelos y precios ficticios.
// Cada foto conserva su proporción y tiene ajustes propios para el encuadre de producto.
const products = [
  {
    id: 'mirador', name: 'Mirador', category: 'gravel', price: 1340,
    description: 'Para llegar a la cima sin plan de regreso.',
    specs: ['Aluminio', 'Neumático 40 mm', 'Salidas largas'],
    image: '/images/mirador-catalog.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1,
    imageBackground: '#d7d8cf', imageWidth: 960, imageHeight: 640,
    alt: 'Bicicleta de gravel en una cumbre frente a un valle amplio'
  },
  {
    id: 'alerce', name: 'Alerce', category: 'gravel', price: 1560,
    description: 'Verde profundo y sillín de cuero, hecha para el camino que se acaba de repente.',
    specs: ['Acero cromoly', 'Neumático 45 mm', 'Mixto asfalto y tierra'],
    image: '/images/alerce.webp', imageFit: 'contain', imagePosition: '48% 50%', imageScale: 1.02,
    imageBackground: '#ded8cc', imageWidth: 1600, imageHeight: 1198,
    alt: 'Bicicleta de gravel verde oscuro con sillín de cuero'
  },
  {
    id: 'cobalto', name: 'Cobalto', category: 'gravel', price: 1290,
    description: 'Un azul intenso para las salidas de fin de semana.',
    specs: ['Aluminio', 'Neumático 42 mm', 'Ruta mixta'],
    image: '/images/cobalto.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1,
    imageBackground: '#e1ddd3', imageWidth: 1600, imageHeight: 1067,
    alt: 'Bicicleta de gravel azul cobalto con neumáticos de pared crema'
  },
  {
    id: 'hierro', name: 'Hierro', category: 'gravel', price: 1890,
    description: 'Titanio sin pintura que se vuelve más suyo con cada kilómetro.',
    specs: ['Titanio', 'Neumático 45 mm', 'Larga distancia'],
    image: '/images/hierro.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1,
    imageBackground: '#d8d3c8', imageWidth: 1600, imageHeight: 1069,
    alt: 'Bicicleta de gravel con cuadro de titanio sin pintar'
  },
  {
    id: 'abeto', name: 'Abeto', category: 'gravel', price: 1420,
    description: 'Verde bosque, líneas limpias y neumáticos de pared marrón.',
    specs: ['Aluminio', 'Neumático 40 mm', 'Uso diario y escapadas'],
    image: '/images/abeto.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1.02,
    imageBackground: '#ddd9ce', imageWidth: 1600, imageHeight: 1200,
    alt: 'Bicicleta de gravel verde bosque sobre pedestal blanco'
  },
  {
    id: 'ruta-clara', name: 'Ruta Clara', category: 'ruta', price: 1050,
    description: 'Ligera y sobria, para las madrugadas con la carretera vacía.',
    specs: ['Aluminio', 'Neumático 28 mm', 'Resistencia'],
    image: '/images/ruta-clara.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1.22,
    imageBackground: '#e6e2d9', imageWidth: 1200, imageHeight: 900,
    alt: 'Bicicleta de ruta blanca apoyada frente a una pared clara'
  },
  {
    id: 'ruta-carmesi', name: 'Ruta Carmesí', category: 'ruta', price: 890,
    description: 'Manillar bajo, geometría tranquila y color de sobra.',
    specs: ['Aluminio', 'Neumático 28 mm', 'Iniciación'],
    image: '/images/ruta-carmesi-catalog.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1,
    imageBackground: '#d8d3ca', imageWidth: 800, imageHeight: 610,
    alt: 'Bicicleta de ruta roja apoyada en una pared de hormigón'
  },
  {
    id: 'ruta-aurora', name: 'Ruta Aurora', category: 'ruta', price: 1480,
    description: 'Cuadro clásico en rojo oscuro y cinta clara en el manillar, para rodar con calma.',
    specs: ['Acero', 'Neumático 28 mm', 'Ritmo constante'],
    image: '/images/ruta-aurora.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 0.98,
    imageBackground: '#796b5d', imageWidth: 1600, imageHeight: 1067,
    alt: 'Bicicleta de ruta roja oscura con cinta clara en el manillar, frente a una cerca de madera'
  },
  {
    id: 'sendero-norte', name: 'Sendero Norte', category: 'montaña', price: 920,
    description: 'Para dejar el asfalto y seguir el sendero sin mirar el reloj.',
    specs: ['Aluminio', 'Rodada 29"', 'Suspensión delantera'],
    image: '/images/sendero-norte.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 0.98,
    imageBackground: '#d5d9ce', imageWidth: 1200, imageHeight: 800,
    alt: 'Bicicleta de montaña oscura en un sendero de bosque'
  },
  {
    id: 'cerro-rojo', name: 'Cerro Rojo', category: 'montaña', price: 1120,
    description: 'Negra con detalles rojos, con ganas de subir y de bajar.',
    specs: ['Aluminio', 'Rodada 29"', 'Cross-country'],
    image: '/images/cerro-rojo.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 0.98,
    imageBackground: '#d4d8d0', imageWidth: 1600, imageHeight: 1200,
    alt: 'Bicicleta de montaña negra con detalles rojos junto a un río'
  },
  {
    id: 'ciudad-verde', name: 'Ciudad Verde', category: 'urbana', price: 640,
    description: 'Posición erguida y guardabarros de serie, para el café de la esquina.',
    specs: ['Acero', 'Guardabarros', 'Trayectos diarios'],
    image: '/images/ciudad-verde.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 1.18,
    imageBackground: '#d4d0c5', imageWidth: 1200, imageHeight: 800,
    alt: 'Bicicleta urbana verde junto a una pared de ladrillo'
  },
  {
    id: 'ciudad-gris', name: 'Ciudad Gris', category: 'urbana', price: 720,
    description: 'Cómoda para cruzar el barrio y volver por la calle bonita.',
    specs: ['Aluminio', 'Cambio interno', 'Trayectos diarios'],
    image: '/images/ciudad-gris.webp', imageFit: 'contain', imagePosition: '50% 50%', imageScale: 0.98,
    imageBackground: '#dce0d5', imageWidth: 1600, imageHeight: 1034,
    alt: 'Bicicleta urbana negra con guardabarros, luces y neumáticos claros'
  }
];
