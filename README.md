# Marketradar

Comparador de precios accesible, con asistente de IA, para adultos mayores en Colombia. La persona escribe qué producto busca y Marketradar le muestra en qué tienda está más barato, con letra grande, lenguaje sencillo y un botón claro para ir a comprar.

Proyecto de aula de **Programación de Computadores III (SS462)**, Universidad Popular del Cesar.
Docente: Ing. Esp. Alfredo Bautista.
Integrantes: Ever Guerra, Gustavo Rodriguez, Luis Corzo y Juan Araujo.

## Funciones del MVP

- Buscar un producto en varias tiendas a la vez (AliExpress, Shopify y Mercado Libre, solo con sus APIs oficiales).
- Ver las ofertas ordenadas por precio total (precio más envío), con la más barata marcada como "Mejor precio".
- Abrir la oferta en el sitio original, con aviso previo.
- Ver el historial de precios de una oferta.
- Preguntarle al asistente de IA cualquier duda en lenguaje sencillo.

No incluye registro de usuarios, pagos ni carrito.

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Frontend | HTML5, CSS3 y JavaScript sin frameworks (mobile-first, WCAG 2.1 AA) |
| Backend | Java 21, Maven, Spring Boot 3 (solo capa web) |
| Acceso a datos | JDBC con patrón DAO (sin JPA), HikariCP |
| Base de datos | PostgreSQL 16 con migraciones Flyway |
| Caché y resiliencia | Caffeine (15 min), Resilience4j (circuit breaker y límite de uso) |
| Pruebas | JUnit 5, Mockito, Testcontainers |
| Despliegue local | Docker y Docker Compose |
