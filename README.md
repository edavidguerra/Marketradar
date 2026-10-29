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

## Arquitectura en 3 capas

```text
Presentación        controller, dto          (+ frontend/)
Lógica de negocio   service, domain, adapter, asistente
Acceso a datos      dao, dao.jdbc, config
```

Cada capa solo se comunica con la inmediatamente inferior. Ver [docs/arquitectura.md](docs/arquitectura.md).

## Estructura del repositorio

```text
marketradar/
├── backend/                       Proyecto Maven (se abre en NetBeans o IntelliJ)
│   ├── pom.xml
│   └── src/
│       ├── main/java/co/edu/unicesar/marketradar/
│       │   ├── controller/        API REST y manejo de errores
│       │   ├── dto/               Objetos de respuesta JSON
│       │   ├── service/           Búsqueda, ofertas, marketplaces y asistente
│       │   ├── domain/            Clases del dominio (Producto, Oferta, ...)
│       │   ├── adapter/           Interfaz, clase base y un adaptador por tienda
│       │   ├── asistente/         Contrato del proveedor de IA del chatbot
│       │   ├── dao/               Interfaces DAO y sus implementaciones JDBC
│       │   ├── config/            Configuración, CORS, seguridad y pool de hilos
│       │   └── exception/         Excepciones propias de cada capa
│       ├── main/resources/
│       │   └── application.yml
│       └── test/                  Pruebas unitarias y de integración
├── frontend/                      Interfaz web accesible (se sirve desde el backend)
├── base-de-datos/                 Asignatura Bases de Datos: solo SQL, no depende del código Java
│   ├── README.md                  Migraciones, reglas para cambiar el esquema y cómo revisarlo
│   └── migraciones/               Scripts V1, V2, ... que Flyway aplica al arrancar el backend
├── docs/                          Documentación por fases
├── docker-compose.yml
└── .env.example                   Plantilla de variables de entorno (sin valores reales)
```

## Organización por asignatura

El repositorio sirve a dos asignaturas y cada una tiene su propia carpeta, sin mezclar responsabilidades:

| Carpeta | Asignatura | Responsabilidad |
| --- | --- | --- |
| `base-de-datos/` | Bases de Datos | Esquema, migraciones y documentación de la base. Solo SQL. |
| `backend/` | Programación de Computadores III | Lógica de negocio en capas y acceso a datos con DAO JDBC. |
| `frontend/` | Programación de Computadores III | Interfaz web accesible para adultos mayores. |
| `docs/` | Ambas | Documentación por fases del proyecto. |

El backend no copia el SQL: al compilar, Maven toma los scripts de `base-de-datos/migraciones` y Flyway los
aplica. Si cambia el esquema, solo cambia `base-de-datos/`; si cambia la lógica, solo cambia `backend/`.

## Cómo ejecutarlo

**Con Docker (recomendado):**

1. Copie `.env.example` como `.env` y cambie la clave de la base de datos.
2. Ejecute `docker compose up --build`.
3. Abra <http://localhost:8080>. La documentación de la API está en <http://localhost:8080/api/docs>.

**Sin Docker:** instale PostgreSQL 16, cree la base `marketradar` y ejecute desde la carpeta `backend`:

```bash
mvn spring-boot:run
```

**Pruebas:** `mvn -f backend/pom.xml verify`. La prueba de los DAO usa Testcontainers y se omite si Docker no está disponible.

## API REST

| Método | Ruta | Función |
| --- | --- | --- |
| GET | `/api/v1/busqueda?q=&orden=&marketplaces=&pagina=&tamano=` | Busca un producto en las tiendas activas |
| GET | `/api/v1/ofertas/{id}` | Detalle de una oferta |
| GET | `/api/v1/ofertas/{id}/historial` | Historial de precios |
| GET | `/api/v1/marketplaces` | Tiendas y su estado |
| GET | `/api/v1/salud` | Estado del servicio, de cada adaptador y del asistente |
| POST | `/api/v1/asistente/mensajes` | Pregunta al asistente de IA |

## Estado actual

- [x] Estructura en 3 capas, modelo de datos, DAO JDBC y migraciones.
- [x] Servicio de búsqueda en paralelo con caché, timeout de 4 s y advertencias por tienda.
- [x] Frontend accesible: inicio, resultados, historial y panel del asistente.
- [ ] Adaptador de AliExpress (pendiente de verificar la API de afiliados).
- [ ] Adaptador de Shopify (pendiente: Catalog API o Storefront MCP).
- [ ] Adaptador de Mercado Libre (pendiente: prueba de acceso OAuth).
- [ ] Proveedor de IA del asistente (pendiente de decisión).

Las decisiones abiertas están en [docs/decisiones-pendientes.md](docs/decisiones-pendientes.md) y el reparto de tareas en [docs/tareas.md](docs/tareas.md).

## Cómo colaborar

Lea [CONTRIBUTING.md](CONTRIBUTING.md) antes de su primer cambio. Resumen: nunca se hace force push, cada funcionalidad va en su propia rama y entra a `main` por pull request revisado por otro integrante.
