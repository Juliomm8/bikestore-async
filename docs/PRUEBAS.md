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

Las pruebas se ejecutaron sin otra instancia de BikeStore y con las colas vacías. Sus preparaciones vacían las colas de este proyecto, por lo que no deben ejecutarse mientras haya pedidos reales pendientes.

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

Las pruebas automatizadas inspeccionaron mensajes y colas reales con RabbitMQ. El éxito manual en 8080 dependió del resultado aleatorio y fue complementado con pruebas deterministas para todos los caminos de pago. La página se comprobó con HTTP y una prueba del contenido servido; no se hizo una prueba visual automatizada del navegador. No se ejecutaron cobros ni envíos de correo reales, de acuerdo con el alcance académico.
