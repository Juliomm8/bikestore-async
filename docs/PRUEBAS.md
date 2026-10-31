# Pruebas y evidencia de BikeStore Async v1.0

Fecha de ejecución: 29 de octubre de 2026, zona horaria `America/Guayaquil`. Se utilizó Java 17, Spring Boot 4.1.2 y el contenedor existente `bikestore-rabbitmq` con RabbitMQ 4. Las marcas `timestamp` de negocio están en UTC.

## Comandos ejecutados

Con las variables `RABBITMQ_USER` y `RABBITMQ_PASSWORD` cargadas desde el `.env` local en PowerShell, sin imprimir sus valores:

```powershell
.\mvnw.cmd test -q
.\mvnw.cmd package -q
docker compose ps
docker exec bikestore-rabbitmq rabbitmqctl list_queues name messages arguments
java -jar target/bikestore-async-0.0.1-SNAPSHOT.jar
```

El paquete y la aplicación iniciaron correctamente. Spring Boot escuchó en el puerto 8080 y se conectó a RabbitMQ en el 5672. `GET /` devolvió `200` y un `POST /api/orders` manual devolvió `202` con `pedidoId=46513cc1-0b3a-4cbb-a18d-a9f64a5ca7e5`. Esa ejecución observó `ORDER_ACCEPTED`, `PAYMENT_ATTEMPT`, `PAYMENT_PAID` y `EMAIL_SENT` para el mismo identificador. La aplicación manual se cerró después de la comprobación.

## Pruebas automatizadas ejecutadas

El último `mvnw.cmd package -q` ejecutó **10 pruebas: 10 correctas, 0 fallos, 0 errores, 0 omitidas**. `OrderApiIntegrationTest` desactiva los consumidores para inspeccionar el pedido original; `PaymentIntegrationTest` usa los consumidores, el RabbitMQ real y decisiones de pago deterministas.

Esta primera serie se ejecutó sin otra instancia de BikeStore y con las colas vacías. Sus preparaciones vacían las colas de este proyecto, por lo que no deben ejecutarse mientras haya pedidos reales pendientes. La serie posterior del frontend se aisló en otro vhost, como se detalla más abajo.

| Escenario | Prueba y resultado observado |
| --- | --- |
| Pedido válido y JSON persistente | `OrderApiIntegrationTest`: HTTP `202`; cuerpo y `pedidoId` coinciden con el mensaje de `bikestore.payment`; `contentType=application/json` y `receivedDeliveryMode=PERSISTENT`. |
| Pedido inválido | `OrderApiIntegrationTest`: HTTP `400` y ningún mensaje nuevo en la cola de pagos. |
| Formulario | `OrderApiIntegrationTest`: `GET /` devuelve `200` y el formulario usa `/api/orders`. |
| Pago exitoso inicial | `PaymentIntegrationTest`: evento `PAID` con `attempts=1`; el consumidor de correo registró la confirmación. |
| Reintentos y éxito posterior | `PaymentIntegrationTest`: fallos en los intentos 1 y 2; éxito `PAID` en el intento 3; exactamente tres llamadas a la decisión de pago. |
| Éxito en el último intento | `PaymentIntegrationTest`: `PAID` en el intento 4 tras tres reintentos, confirmación de correo y ausencia en la DLQ. |
| Fallo definitivo | `PaymentIntegrationTest`: cuatro fallos, JSON `FailedPayment` en `bikestore.payment.dead` con `pedidoId`, `attempts=4`, motivo y cabecera `failedAttempts=4`; sin evento `PAID` ni correo. |
| Correo solo para pagados | `PaymentIntegrationTest`: un evento sintético `PENDING` se consumió sin enviar confirmación. |
| Trazabilidad | `OrderEventLoggerTest`: el registro incluye `pedidoId`, `timestamp` ISO y `thread`. |
| Inicio de contexto | `BikestoreAsyncApplicationTests`: contexto Spring Boot iniciado. |

Se comprobó además la topología real de RabbitMQ: `bikestore.payment`, `bikestore.payment.retry`, `bikestore.email` y `bikestore.payment.dead` existían. La cola de reintento tenía `x-message-ttl=750` y retorno a `bikestore.orders`; la de pagos tenía dead-letter exchange `bikestore.dead`. Las cuatro colas estaban vacías después de las pruebas.

## Fragmentos de logs observados

Las siguientes líneas provienen de la ejecución automatizada. Se usa el mismo `pedidoId` para seguir dos fallos, el éxito y el correo; no contienen credenciales:

```text
pedidoId=4f95be6c-c76c-41d8-8521-dc56ac27dea9 timestamp=2026-10-29T09:00:51.427385300Z thread=org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#1-1 evento=PAYMENT_FAILED detalle=Cobro simulado rechazado en intento 1
pedidoId=4f95be6c-c76c-41d8-8521-dc56ac27dea9 timestamp=2026-10-29T09:00:52.181084300Z thread=org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#1-1 evento=PAYMENT_ATTEMPT detalle=Intento 2/4
pedidoId=4f95be6c-c76c-41d8-8521-dc56ac27dea9 timestamp=2026-10-29T09:00:52.938332200Z thread=org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#1-1 evento=PAYMENT_PAID detalle=Cobro simulado exitoso en intento 3
pedidoId=4f95be6c-c76c-41d8-8521-dc56ac27dea9 timestamp=2026-10-29T09:00:52.942370200Z thread=org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1 evento=EMAIL_SENT detalle=Confirmación simulada en prueba
```

## Alcance de la comprobación

Las pruebas automatizadas inspeccionaron mensajes y colas reales con RabbitMQ. El éxito manual en 8080 dependió del resultado aleatorio y fue complementado con pruebas deterministas para todos los caminos de pago. No se ejecutaron cobros ni envíos de correo reales, de acuerdo con el alcance académico.

## Revisión del catálogo y formulario

El 29 de octubre de 2026 se ejecutó de nuevo `node --check src/main/resources/static/js/store.js` y `./mvnw.cmd package -q` desde PowerShell. El paquete terminó con **10 pruebas correctas, 0 fallos, 0 errores y 0 omitidas**. `OrderApiIntegrationTest` comprobó además que `GET /`, `/js/store.js`, `/css/store.css` y `/images/ruta-clara.jpg` se sirven con HTTP `200`.

Había otra instancia de BikeStore activa en el puerto 8080. Para evitar que sus consumidores interfirieran con las pruebas de RabbitMQ, se creó el vhost `bikestore_frontend_test` dentro del contenedor **propio** `bikestore-rabbitmq`, se dieron permisos al usuario local de BikeStore y se ejecutó Maven con `SPRING_RABBITMQ_VIRTUAL_HOST=bikestore_frontend_test`. No se detuvo ni modificó ningún contenedor ajeno. El comando de prueba fue:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([^#=]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
  }
}
$env:SPRING_RABBITMQ_VIRTUAL_HOST = 'bikestore_frontend_test'
node --check src/main/resources/static/js/store.js
.\mvnw.cmd package -q
```

La revisión manual se hizo en Chromium contra `http://localhost:8080/`, con el backend real activo. Se inspeccionaron capturas de página completa a **1440 × 900** y **390 × 844**. También se midió el ancho del documento a **320** y **768 px**: no hubo desbordamiento horizontal. Las seis fotografías locales se cargaron y la consola no registró errores ni advertencias.

| Escenario del frontend | Resultado observado en navegador |
| --- | --- |
| Filtro Ruta | El catálogo mostró exactamente dos bicicletas y el filtro cambió a `aria-pressed=true`. |
| Selección y total | Al elegir `Ruta Clara`, el panel mostró modelo y precio unitario `$1.050,00`; con cantidad `2`, el total pasó a `$2.100,00`. |
| Correo inválido | `invalido` mostró `Ingresa un correo electrónico válido.` sin enviar un POST. |
| Cantidad inválida | `0` eliminó el total y mostró `Ingresa una cantidad entera de al menos 1.` |
| Estado de carga | Durante el POST, el botón quedó deshabilitado con el texto `Enviando pedido` y apareció `Registrando tu pedido…`. |
| Pedido válido | Se envió `{"customerEmail":"frontend.qa@example.com","bikeModel":"Ruta Clara","quantity":2,"unitPrice":1050}`. El API devolvió HTTP `202`, `status=ACCEPTED` y `pedidoId=1b6f1ee4-add1-47be-b556-788f067ccf40`; la interfaz mostró ese ID y explicó que el pago se procesa de forma asíncrona. |
| Accesibilidad básica | `Tab` enfocó el enlace de salto con contorno visible; los campos tenían etiquetas, las imágenes cargadas tenían texto alternativo y no hubo IDs duplicados. |

Las capturas y el registro de revisión se guardaron localmente en `target/ui-review/` (directorio ignorado por Git). El verificador de evidencias del flujo de diseño informó `Evidence record complete` tras revisar los dos tamaños, estados y artefactos. Esta comprobación visual fue manual; los mensajes y la respuesta HTTP sí se observaron en el navegador real.

## Rediseño visual (29 de octubre de 2026)

Se ejecutó `mvnw.cmd package -q` con las variables de `.env` cargadas, `RABBITMQ_HOST=127.0.0.1` y las colas vacías: **10 pruebas correctas, 0 fallos**. La prueba `orderFormIsServed` se actualizó a los nuevos nombres de clases CSS (`.card`, `.order__panel`).

Con la aplicación real y Google Chrome controlado por Playwright (escritorio 1440×900 y móvil 390×844):

| Comprobación | Resultado |
| --- | --- |
| Carga de portada | Sin errores de consola ni de red (se añadió un favicon para evitar un 404). |
| Filtro Gravel | Mostró 5 bicicletas. |
| Selección de `Alerce` y cantidad 2 | Total `$3.120,00`; la barra de selección llevó al pedido. |
| Envío | Cuerpo `{"customerEmail":"test@example.com","bikeModel":"Alerce","quantity":2,"unitPrice":1560}`; el API respondió 202 y la interfaz mostró el `pedidoId` aclarando que el pago aún no se ha procesado. |
| Menú móvil | Se abre, muestra los tres enlaces, cambia a «Cerrar» y se cierra con Esc o al elegir un enlace. |
| Desbordamiento horizontal | No hubo en 390 px en portada, selección, catálogo, pedido y pie. |

No se probó con lectores de pantalla ni en Firefox/Safari. Las animaciones respetan `prefers-reduced-motion` por CSS y JavaScript, pero esa preferencia no se activó en el navegador durante la revisión.

## Encuadres y revisión responsive (29 de octubre de 2026)

Se inspeccionaron individualmente los doce archivos de producto y las tres imágenes de la selección en Chromium, con Spring Boot ya activo en `http://localhost:8080/`. Las fotos locales de catálogo, destacados y pedido se comprobaron con el encuadre final. Las capturas completas se guardaron bajo `target/ui-review/capturas/`; ese directorio está ignorado por Git y no forma parte de la entrega versionada.

| Vista comprobada | Composición medida | Carga y desbordamiento |
| --- | --- | --- |
| 360 × 800 | Catálogo de una columna; cabecera y pedido en una columna; foto de cabecera con `contain`. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |
| 390 × 844 | Catálogo de una columna; cabecera y pedido en una columna; foto de cabecera con `contain`. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |
| 768 × 1024 | Catálogo de dos columnas; selección apilada; pedido de una columna. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |
| 1366 × 900 | Catálogo de tres columnas; pedido de dos columnas; cabecera panorámica. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |
| 1440 × 900 | Catálogo de tres columnas; pedido de dos columnas; cabecera panorámica. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |
| 1920 × 1080 | Catálogo de tres columnas; pedido de dos columnas; cabecera panorámica. | 12 fichas y 16 imágenes locales cargadas; sin desplazamiento horizontal. |

Los nombres de las capturas son `360-movil.jpeg`, `390-movil.jpeg`, `768-tablet.jpeg`, `1366-portatil.jpeg`, `1440-escritorio.jpeg` y `1920-pantalla-grande.jpeg`. A 360, 390 y 768 px la cabecera usa una variante local más cercana de Mirador con `object-fit: contain`; en escritorio mantiene la toma panorámica. Las tarjetas y el resumen del pedido mantienen las doce bicicletas completas, sin deformarlas. En todas las imágenes de catálogo se verificó que la imagen decodificada corresponde al archivo local esperado.

| Interacción comprobada en móvil | Resultado observado |
| --- | --- |
| Menú con teclado | El botón pasó `aria-expanded` a `true`, mostró sus tres enlaces; `Escape` lo cerró y devolvió el foco al botón. |
| Filtro Montaña | Mostró exactamente `Sendero Norte` y `Cerro Rojo`; al restaurar «Todas» aparecieron las doce bicicletas. |
| Selección y cantidad | `Ruta Carmesí`, cantidad 2, precio unitario `$890,00` y total `$1.780,00`. El resumen cargó `ruta-carmesi-catalog.jpg` (800 × 610) con `object-fit: contain`. |
| Correo inválido | `no-es-correo` mostró `Ingresa un correo electrónico válido.` y no envió un POST. |
| Áreas táctiles | Botón de menú y botones «Elegir» de al menos 44 px; filtros de 48 px en la medición de 360 px. |
| Servidor y consola | `GET /` devolvió HTTP `200`; `HEAD /images/ruta-carmesi-catalog.jpg` devolvió HTTP `200` (`image/jpeg`); Chromium no mostró errores ni advertencias de consola. |

Comprobaciones de código ejecutadas desde PowerShell:

```powershell
node --check src/main/resources/static/js/catalog.js
node --check src/main/resources/static/js/store.js
git diff --check
.\mvnw.cmd -q -Dtest=OrderEventLoggerTest test
.\mvnw.cmd -q -DskipTests package
```

Los dos `node --check`, `git diff --check`, la prueba unitaria `OrderEventLoggerTest` (1 correcta) y el empaquetado Maven terminaron correctamente. No se ejecutaron en esta revisión `OrderApiIntegrationTest` ni `PaymentIntegrationTest`: hay una instancia de BikeStore activa en el puerto 8080 y las preparaciones de esas pruebas vacían colas RabbitMQ compartidas. La revisión no creó pedidos ni purgó colas. El bloque CSS `prefers-reduced-motion` y la consulta JavaScript `matchMedia` siguen presentes; el control Chromium disponible no ofrece emulación de esa preferencia, por lo que no se afirma que se haya comprobado visualmente con el sistema operativo configurado para movimiento reducido.

## Carrito múltiple, caché y rendimiento (31 de octubre de 2026)

### Pruebas automatizadas

Se ejecutaron desde PowerShell:

```powershell
.\mvnw.cmd -q "-Dtest=OrderControllerMvcTest,OrderControllerTest,OrderSubmissionServiceTest,OrderProducerTest,PaymentWorkerTest,EmailWorkerTest,OrderEventLoggerTest,AssetCacheFilterTest" test
.\mvnw.cmd -q -DskipTests package
node --check src/main/resources/static/js/catalog.js
node --check src/main/resources/static/js/store.js
git diff --check
```

El comando Maven dirigido terminó con **18 pruebas correctas, 0 fallos, 0 errores y 0 omitidas**. El empaquetado también terminó correctamente y compiló las pruebas de integración. Las pruebas dirigidas no conectan a RabbitMQ: comprueban que se acepta el carrito en HTTP `202`, devuelve un único identificador y el total del servidor; las validaciones; los precios autoritativos; una sola publicación por pedido; pagos deterministas con éxito tras reintentos o con cuarto fallo a DLQ; el correo solo para `PAID`; trazabilidad de logs; y los encabezados de caché.

`OrderProducerTest` verifica una sola llamada al publicador y que el mensaje contiene todos los artículos y el total. `PaymentWorkerTest` cubre el éxito posterior a reintentos y la DLQ al cuarto intento, conservando el pedido completo. Estos son resultados automatizados con dependencias simuladas, no una afirmación de que esta versión se haya publicado en RabbitMQ.

### Comprobaciones del navegador

Se revisó la instancia empaquetada en Chromium mediante Playwright, sin enviar un POST de compra. El flujo interactivo observado fue:

| Escenario | Resultado |
| --- | --- |
| Añadir Alerce × 2 y Ruta Clara × 1 | Dos modelos en el carrito; total `$4.170,00`. |
| Cambiar Alerce a 3 | Total recalculado a `$5.730,00`. |
| Quitar Ruta Clara | Queda Alerce × 3; total `$4.680,00`. |
| Cantidad 0 | Total pasa a `—` y se deshabilita la confirmación. |
| Correo inválido | `aria-invalid=true`, mensaje «Ingresa un correo electrónico válido.»; no se envía el formulario. |
| Filtro Ruta y restablecer | Muestra 3 modelos; «Todas» vuelve a mostrar los 12. |
| Imágenes y consola | Las 12 imágenes locales de catálogo cargaron como WebP; sin errores ni advertencias de consola. |

Se midió el ancho del documento y el catálogo con el carrito abierto en estos viewports: 360, 390, 768, 1366 y 1920 px. No hubo desbordamiento horizontal; el catálogo mostró una columna en móvil, dos en tablet y tres en escritorio. En todos los anchos siguieron visibles el total, la cantidad y el campo de correo. El menú móvil abrió y cerró con su estado accesible.

### RabbitMQ y límites de esta ejecución

`docker compose ps` mostró `bikestore-rabbitmq` saludable. Antes y después de estas comprobaciones, `rabbitmqctl list_queues name messages consumers` mostró cero mensajes pendientes; la instancia que ya estaba abierta mantenía un consumidor conectado a `bikestore.payment` y otro a `bikestore.email`. No se detuvo esa instancia, no se publicaron pedidos y no se purgaron colas.

Por esa instancia activa no se ejecutó `OrderApiIntegrationTest`, que publica un pedido en la cola compartida, ni `PaymentIntegrationTest`, que purga las colas de pagos, reintentos, correo y DLQ al preparar sus casos. En esta revisión no se verificaron con mensajes reales de RabbitMQ el JSON publicado ni la ubicación real del cuarto fallo en la DLQ. El endpoint se verificó con MockMvc; productores y trabajadores con pruebas deterministas sin broker.

Para comprobar HTTP sin competir por los consumidores se inició temporalmente una instancia en el puerto 8082 con los listeners RabbitMQ desactivados. `GET /` devolvió `200`; los `HEAD` a CSS, JavaScript, WebP y WOFF2 versionados devolvieron `200`. `HEAD /api/orders` devolvió `405` (método no permitido) y conservó `Cache-Control: private, no-store, no-cache, must-revalidate`. No se hizo una petición `POST` al broker activo.

| Recurso | `Cache-Control` observado |
| --- | --- |
| HTML de `/` | `no-cache, must-revalidate` |
| CSS, JavaScript, WebP y WOFF2 con `?v=20261031-3` | `public, max-age=31536000, immutable` |
| `/api/**` | `private, no-store, no-cache, must-revalidate` |

También se ejecutó `AssetCacheFilterTest`, que comprueba caché de un año solo para archivos versionados, revalidación para recursos sin versión y no almacenamiento de respuestas API. No se guarda en caché el contenido de pedidos ni pagos.

### Optimización de imágenes

Se midieron los 13 archivos de imagen servidos (doce fotos de catálogo y la variante de Mirador para la portada). Las conversiones WebP mantuvieron las dimensiones originales, con calidad individual 82–88. Al compararlas con los originales mediante FFmpeg SSIM, el rango fue **0,9807–0,9891**. El peso conjunto pasó de **4,57 MiB a 3,77 MiB** (**17,5 % menos**). La procedencia, las licencias y las variantes están en [docs/IMAGENES.md](IMAGENES.md).

### Lighthouse

Auditorías locales realizadas el 31 de octubre de 2026 con Lighthouse CLI **13.6.0**, Edge headless y el servidor empaquetado en `127.0.0.1:8082`. La línea base se había medido en el mismo proyecto antes de la conversión WebP y del filtro de caché. Lighthouse dio puntuación de rendimiento 100 en todas las ejecuciones; el peso de transferencia bajó en ambas vistas. Los tiempos móviles variaron entre las dos corridas finales, por eso se muestran ambas.

| Vista | Ejecución | Rendimiento | FCP | LCP | TBT | CLS | Speed Index | Peso transferido |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Móvil | Antes | 100 | 0,9 s | 0,9 s | 0 ms | 0,027 | 0,9 s | 1.486 KiB |
| Móvil | Después, corrida 1 | 100 | 1,2 s | 1,2 s | 30 ms | 0,027 | 1,2 s | 1.309 KiB |
| Móvil | Después, corrida 2 | 100 | 0,9 s | 0,9 s | 0 ms | 0,027 | 0,9 s | 1.309 KiB |
| Escritorio | Antes | 100 | 0,3 s | 0,3 s | 0 ms | 0 | 0,4 s | 2.887 KiB |
| Escritorio | Después | 100 | 0,2 s | 0,2 s | 0 ms | 0 | 0,4 s | 2.575 KiB |

La transferencia auditada se redujo **177 KiB (11,9 %) en móvil** y **312 KiB (10,8 %) en escritorio**. Los informes JSON de Lighthouse permanecen bajo `target/performance/`, ignorado por Git.

Comandos finales de auditoría usados en PowerShell:

```powershell
npx --yes lighthouse http://127.0.0.1:8082/ --chrome-path="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --chrome-flags="--headless=new --disable-gpu --no-sandbox" --output=json --output-path=target/performance/final-mobile-repeat.json --quiet
npx --yes lighthouse http://127.0.0.1:8082/ --preset=desktop --chrome-path="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --chrome-flags="--headless=new --disable-gpu --no-sandbox" --output=json --output-path=target/performance/final-desktop.json --quiet
```
