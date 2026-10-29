// Catálogo de demostración: modelos y precios ficticios.
// `focus` ajusta el encuadre de la fotografía dentro de la ficha (object-position).
const products = [
  {
    id: 'mirador', name: 'Mirador', category: 'gravel', price: 1340,
    description: 'Para llegar a la cima sin plan de regreso.',
    specs: ['Aluminio', 'Neumático 40 mm', 'Salidas largas'],
    image: '/images/mirador.jpg', focus: '38% 62%',
    alt: 'Bicicleta de gravel en una cumbre frente a un valle amplio'
  },
  {
    id: 'alerce', name: 'Alerce', category: 'gravel', price: 1560,
    description: 'Verde profundo y sillín de cuero, hecha para el camino que se acaba de repente.',
    specs: ['Acero cromoly', 'Neumático 45 mm', 'Mixto asfalto y tierra'],
    image: '/images/alerce.jpg', focus: '45% 60%',
    alt: 'Bicicleta de gravel verde oscuro con sillín de cuero'
  },
  {
    id: 'cobalto', name: 'Cobalto', category: 'gravel', price: 1290,
    description: 'Un azul intenso para las salidas de fin de semana.',
    specs: ['Aluminio', 'Neumático 42 mm', 'Ruta mixta'],
    image: '/images/cobalto.jpg', focus: '50% 60%',
    alt: 'Bicicleta de gravel azul cobalto con neumáticos de pared crema'
  },
  {
    id: 'hierro', name: 'Hierro', category: 'gravel', price: 1890,
    description: 'Titanio sin pintura que se vuelve más suyo con cada kilómetro.',
    specs: ['Titanio', 'Neumático 45 mm', 'Larga distancia'],
    image: '/images/hierro.jpg', focus: '50% 58%',
    alt: 'Bicicleta de gravel con cuadro de titanio sin pintar'
  },
  {
    id: 'abeto', name: 'Abeto', category: 'gravel', price: 1420,
    description: 'Verde bosque, líneas limpias y neumáticos de pared marrón.',
    specs: ['Aluminio', 'Neumático 40 mm', 'Uso diario y escapadas'],
    image: '/images/abeto.jpg', focus: '50% 62%',
    alt: 'Bicicleta de gravel verde bosque sobre pedestal blanco'
  },
  {
    id: 'ruta-clara', name: 'Ruta Clara', category: 'ruta', price: 1050,
    description: 'Ligera y sobria, para las madrugadas con la carretera vacía.',
    specs: ['Aluminio', 'Neumático 28 mm', 'Resistencia'],
    image: '/images/ruta-clara.jpg', focus: '50% 55%',
    alt: 'Bicicleta de ruta blanca apoyada frente a una pared clara'
  },
  {
    id: 'ruta-carmesi', name: 'Ruta Carmesí', category: 'ruta', price: 890,
    description: 'Manillar bajo, geometría tranquila y color de sobra.',
    specs: ['Aluminio', 'Neumático 28 mm', 'Iniciación'],
    image: '/images/ruta-carmesi.jpg', focus: '50% 55%',
    alt: 'Bicicleta de ruta roja apoyada en una pared de hormigón'
  },
  {
    id: 'ruta-aurora', name: 'Ruta Aurora', category: 'ruta', price: 1480,
    description: 'Tubos perfilados y cuadro blanco con detalles rojos.',
    specs: ['Aluminio aerodinámico', 'Neumático 32 mm', 'Ritmo constante'],
    image: '/images/ruta-aurora.jpg', focus: '40% 60%',
    alt: 'Bicicleta de ruta blanca y roja con tubos perfilados'
  },
  {
    id: 'sendero-norte', name: 'Sendero Norte', category: 'montaña', price: 920,
    description: 'Para dejar el asfalto y seguir el sendero sin mirar el reloj.',
    specs: ['Aluminio', 'Rodada 29"', 'Suspensión delantera'],
    image: '/images/sendero-norte.jpg', focus: '50% 60%',
    alt: 'Bicicleta de montaña oscura en un sendero de bosque'
  },
  {
    id: 'cerro-rojo', name: 'Cerro Rojo', category: 'montaña', price: 1120,
    description: 'Negra con detalles rojos, con ganas de subir y de bajar.',
    specs: ['Aluminio', 'Rodada 29"', 'Cross-country'],
    image: '/images/cerro-rojo.jpg', focus: 'center 73%',
    alt: 'Bicicleta de montaña negra y roja junto a un muro exterior'
  },
  {
    id: 'ciudad-verde', name: 'Ciudad Verde', category: 'urbana', price: 640,
    description: 'Posición erguida y guardabarros de serie, para el café de la esquina.',
    specs: ['Acero', 'Guardabarros', 'Trayectos diarios'],
    image: '/images/ciudad-verde.jpg', focus: '50% 55%',
    alt: 'Bicicleta urbana verde junto a una pared de ladrillo'
  },
  {
    id: 'ciudad-gris', name: 'Ciudad Gris', category: 'urbana', price: 720,
    description: 'Cómoda para cruzar el barrio y volver por la calle bonita.',
    specs: ['Aluminio', 'Cambio interno', 'Trayectos diarios'],
    image: '/images/ciudad-gris.jpg', focus: '50% 55%',
    alt: 'Bicicleta urbana gris estacionada en una calle soleada'
  }
];
