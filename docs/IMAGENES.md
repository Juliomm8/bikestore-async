# Procedencia y optimización de las fotografías

Las doce fotografías de producto y la imagen principal se alojan en `src/main/resources/static/images/`. Son imágenes ilustrativas: nombres, descripciones, especificaciones y precios de BikeStore son ficticios y no identifican las marcas fotografiadas.

## Créditos y licencias

Las adaptaciones WebP conservan la atribución y licencia de sus originales. Los archivos con licencia CC BY-SA también se comparten bajo esa licencia.

| Archivo WebP | Fotografía y autor | Licencia y origen |
| --- | --- | --- |
| `sendero-norte.webp` | [Bicicleta de montaña en un bosque](https://unsplash.com/photos/a-mountain-bike-parked-in-a-wooded-area-PXYFdwNLB1E), Alexander Fastovets | [Licencia de Unsplash](https://unsplash.com/license) |
| `ruta-clara.webp` | [Bicicleta blanca de ruta](https://unsplash.com/photos/a-white-bicycle-with-black-wheels-4TTn6Pu7Fjk), Michael Martinelli | [Licencia de Unsplash](https://unsplash.com/license) |
| `ruta-carmesi-catalog.webp` | Recorte local de la [bicicleta roja de ruta](https://unsplash.com/photos/red-and-black-drop-handled-road-bike-leaning-on-gray-concrete-wall-during-daytime-CrAQNQ4edUc), Ali Tawfiq | Recorte de 800 × 610 px; [licencia de Unsplash](https://unsplash.com/license) |
| `ciudad-verde.webp` | [Bicicleta urbana verde](https://unsplash.com/photos/green-commuter-bike-kxuAteLDmfk), Patricia Tsernoshova | [Licencia de Unsplash](https://unsplash.com/license) |
| `mirador.webp` | [At the top of Puech des Mourgues](https://commons.wikimedia.org/wiki/File:At_the_top_of_Puech_des_Mourgues.jpg), N1k0mmons | CC0 |
| `mirador-catalog.webp` | Recorte de [At the top of Puech des Mourgues](https://commons.wikimedia.org/wiki/File:At_the_top_of_Puech_des_Mourgues.jpg), N1k0mmons | CC0; recorte de 960 × 640 px |
| `alerce.webp` | [Eurobike 2024, Frankfurt am Main](https://commons.wikimedia.org/wiki/File:Eurobike_2024,_Frankfurt_am_Main_(LRM_20240707_150525).jpg), Matti Blume | CC BY-SA 4.0 |
| `cobalto.webp` | [Mikamaro ocean blue, Cyclingworld Europe 2024](https://commons.wikimedia.org/wiki/File:Mikamaro_ocean_blue,_Cyclingworld_Europe_2024,_Meerbusch_(P1170973-RR).jpg), Matti Blume | CC BY-SA 4.0 |
| `hierro.webp` | [Titanium frame gravel bicycle](https://commons.wikimedia.org/wiki/File:Titanium-frame-gravel-bicycle.jpg), Jeff Dieffenbach | CC BY 4.0 |
| `abeto.webp` | [Bicycle, IAA Summit 2023, Munich](https://commons.wikimedia.org/wiki/File:Bicycle,_IAA_Summit_2023,_Munich_(P1110756).jpg), Matti Blume | CC BY-SA 4.0 |
| `ruta-aurora.webp` | [Mercian road bike profile shot](https://commons.wikimedia.org/wiki/File:Mercian_road_bike_profile_shot.jpg), Parrot of Doom | CC BY-SA 3.0 |
| `cerro-rojo.webp` | [Single-speed mountain bike](https://commons.wikimedia.org/wiki/File:Single-speed_mountain_bike.jpg), PAGreenwood | CC BY-SA 4.0 |
| `ciudad-gris.webp` | [Dutch bicycle](https://commons.wikimedia.org/wiki/File:Dutch_bicycle.jpg), Petar Milošević | CC BY-SA 4.0 |

## Presentación y formato

Las fotografías originales se inspeccionaron antes de convertirlas. Las tarjetas, destacadas y el resumen del pedido conservan `object-fit: contain` y proporciones nativas; el catálogo usa marcos 3:2 con fondo por modelo. La fotografía panorámica de Mirador se conserva para escritorio; en pantallas de hasta 960 px se muestra el recorte local con ambos neumáticos visibles. Cada modelo configura de forma independiente `imageFit`, `imagePosition`, `imageScale` e `imageBackground` en `static/js/catalog.js`.

Las imágenes activas se convirtieron localmente a WebP con calidad por imagen (82–88) y sin cambiar sus dimensiones. Se eligió la calidad individual para conservar similitud estructural SSIM superior a 0,98 en las trece salidas comparadas con su original; los tamaños del conjunto activo bajaron de 4,57 MiB a 3,77 MiB (17,5 %). Las doce imágenes de catálogo cargaron como WebP en Chromium sin errores de consola. Las fotografías mantienen texto alternativo descriptivo.

Los tres archivos que reemplazaron tomas problemáticas conservan sus atribuciones: `ruta-aurora.webp` ahora muestra el perfil completo; `cerro-rojo.webp` no corta la rueda delantera; y `ciudad-gris.webp` presenta la bicicleta completa sin barreras que la oculten. Cada modelo mantiene una fotografía distinta.

## Tipografías

Cormorant Garamond para titulares y Hanken Grotesk para texto se alojan en `static/fonts/` en WOFF2 desde Fontsource, bajo licencia SIL OFL 1.1.
