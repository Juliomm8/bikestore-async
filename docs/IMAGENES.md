# Procedencia de las fotografías

Las doce fotografías del catálogo se guardan en `src/main/resources/static/images/` para que la tienda funcione sin pedir imágenes a servidores externos. Son **imágenes ilustrativas**: los nombres, descripciones y precios de BikeStore son ficticios y no identifican los modelos ni las marcas fotografiadas.

Cuatro imágenes se descargaron de Unsplash y se usan bajo la [licencia de Unsplash](https://unsplash.com/license). Se acredita a cada autor aunque la licencia no lo exige.

| Archivo local | Fotografía y autor |
| --- | --- |
| `sendero-norte.jpg` | [Bicicleta de montaña en un bosque](https://unsplash.com/photos/a-mountain-bike-parked-in-a-wooded-area-PXYFdwNLB1E), Alexander Fastovets |
| `ruta-clara.jpg` | [Bicicleta blanca de ruta](https://unsplash.com/photos/a-white-bicycle-with-black-wheels-4TTn6Pu7Fjk), Michael Martinelli |
| `ruta-carmesi.jpg` | [Bicicleta roja de ruta](https://unsplash.com/photos/red-and-black-drop-handled-road-bike-leaning-on-gray-concrete-wall-during-daytime-CrAQNQ4edUc), Ali Tawfiq |
| `ruta-carmesi-catalog.jpg` | Recorte local de la fotografía de [Ruta Carmesí](https://unsplash.com/photos/red-and-black-drop-handled-road-bike-leaning-on-gray-concrete-wall-during-daytime-CrAQNQ4edUc), Ali Tawfiq (800 × 610) |
| `ciudad-verde.jpg` | [Bicicleta urbana verde](https://unsplash.com/photos/green-commuter-bike-kxuAteLDmfk), Patricia Tsernoshova |

Las fotografías de Unsplash se guardaron localmente como JPEG de aproximadamente 1200 px de ancho. La interfaz conserva un texto alternativo descriptivo para cada imagen.

## Fotografías añadidas en el rediseño

Las fotografías siguientes proceden de [Wikimedia Commons](https://commons.wikimedia.org/). Las tres incorporadas para corregir productos que aparecían cortados o demasiado pequeños se redimensionaron localmente a 1600 px de ancho y se guardaron como JPEG. Las licencias CC BY y CC BY-SA exigen atribución; las CC BY-SA además exigen compartir las adaptaciones bajo la misma licencia. Los nombres y precios del catálogo son ficticios: las imágenes son ejemplos y no identifican los modelos ni sus especificaciones.

| Archivo local | Fotografía y autor | Licencia | Tamaño local |
| --- | --- | --- | --- |
| `mirador.jpg` | [At the top of Puech des Mourgues](https://commons.wikimedia.org/wiki/File:At_the_top_of_Puech_des_Mourgues.jpg), N1k0mmons | CC0 | 1600 × 1200 |
| `mirador-catalog.jpg` | Recorte de [At the top of Puech des Mourgues](https://commons.wikimedia.org/wiki/File:At_the_top_of_Puech_des_Mourgues.jpg), N1k0mmons | CC0 | 960 × 640 |
| `alerce.jpg` | [Eurobike 2024, Frankfurt am Main](https://commons.wikimedia.org/wiki/File:Eurobike_2024,_Frankfurt_am_Main_(LRM_20240707_150525).jpg), Matti Blume | CC BY-SA 4.0 | 1600 × 1198 |
| `cobalto.jpg` | [Mikamaro ocean blue, Cyclingworld Europe 2024](https://commons.wikimedia.org/wiki/File:Mikamaro_ocean_blue,_Cyclingworld_Europe_2024,_Meerbusch_(P1170973-RR).jpg), Matti Blume | CC BY-SA 4.0 | 1600 × 1067 |
| `hierro.jpg` | [Titanium frame gravel bicycle](https://commons.wikimedia.org/wiki/File:Titanium-frame-gravel-bicycle.jpg), Jeff Dieffenbach | CC BY 4.0 | 1600 × 1069 |
| `abeto.jpg` | [Bicycle, IAA Summit 2023, Munich](https://commons.wikimedia.org/wiki/File:Bicycle,_IAA_Summit_2023,_Munich_(P1110756).jpg), Matti Blume | CC BY-SA 4.0 | 1600 × 1200 |
| `ruta-aurora.jpg` | [Mercian road bike profile shot](https://commons.wikimedia.org/wiki/File:Mercian_road_bike_profile_shot.jpg), Parrot of Doom | CC BY-SA 3.0 | 1600 × 1067 |
| `cerro-rojo.jpg` | [Single-speed mountain bike](https://commons.wikimedia.org/wiki/File:Single-speed_mountain_bike.jpg), PAGreenwood | CC BY-SA 4.0 | 1600 × 1200 |
| `ciudad-gris.jpg` | [Dutch bicycle](https://commons.wikimedia.org/wiki/File:Dutch_bicycle.jpg), Petar Milošević | CC BY-SA 4.0 | 1600 × 1034 |

## Revisión de encuadre

Las doce imágenes de producto se revisaron a tamaño original antes de ajustar su presentación. En catálogo, destacadas y resumen del pedido se usa `object-fit: contain` por defecto para preservar la fotografía completa; las fichas reservan un marco común 3:2 y dejan que cada imagen mantenga su proporción. Mirador conserva el paisaje panorámico en escritorio y usa un recorte local más cercano en catálogo y en la cabecera móvil/tablet, con ambos neumáticos visibles. `imageFit`, `imagePosition`, `imageScale` y `imageBackground` se configuran por modelo en `static/js/catalog.js`. Las imágenes de producto y los botones de selección tienen áreas suficientes para pantallas táctiles.

Se sustituyeron tres recursos problemáticos: `ruta-aurora.jpg` (la rueda trasera quedaba fuera de la toma), `cerro-rojo.jpg` (la rueda delantera estaba cortada en el borde inferior) y `ciudad-gris.jpg` (la bicicleta quedaba pequeña y cruzada por barreras). Las nuevas fotografías tienen encuadre lateral completo y no comparten archivo con otros modelos.

## Tipografías

Se alojan localmente en `static/fonts/` (archivos woff2 de Fontsource, licencia SIL OFL 1.1): Cormorant Garamond (titulares) y Hanken Grotesk (texto).
