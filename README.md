# Marketradar

Comparador de precios accesible, con asistente de IA, para adultos mayores en Colombia. La persona escribe qué producto busca y Marketradar le muestra en qué tienda está más barato, con letra grande, lenguaje sencillo y un botón claro para ir a comprar.

Proyecto de aula de **Programación de Computadores III (SS462)**, Universidad Popular del Cesar.
Docente: Ing. Esp. Alfredo Bautista.
Integrantes: Ever Guerra, Gustavo Rodriguez, Luis Corzo y Juan Araujo.

## Funciones

**Ingreso con dos interfaces.** La persona crea su cuenta con correo y una contraseña segura (minúscula, mayúscula, número y carácter especial) y entra a la interfaz de usuario. El administrador entra con el mismo formulario y llega a su propia interfaz. Cada rol solo puede usar su parte de la API.

**Interfaz de usuario** (letra grande, alto contraste, botones grandes y lenguaje sencillo):

- **Inicio:** saludo y un aviso dinámico que invita a comprar (lo que buscó hace poco, productos que bajaron de precio o una invitación a buscar).
- **Buscar:** compara el producto en todas las tiendas activas y muestra primero la **mejor opción de compra (producto MVP)**, elegida por precio total, reseñas y envío, con las razones y un botón que abre exactamente ese producto en la tienda. Después lista todas las ofertas y su historial de precio.
- **Historial:** las búsquedas de la persona, para repetirlas con un clic o borrarlas.
- **Mi perfil:** formulario estándar (nombres, apellidos, documento, fecha de nacimiento, celular y ciudad) y cambio de contraseña.
- **PQR:** peticiones, quejas, reclamos y sugerencias, con número de radicado y la respuesta del administrador.
- **Tutorial:** explica paso a paso para qué sirve cada opción.
- **Asistente:** chatbot con Spring AI que consulta precios reales y responde en frases cortas.

**Interfaz de administrador:** resumen de PQR y usuarios, lista de PQR con el nombre y el correo de quien la envió, filtro por estado y respuesta.

**Moneda:** todo se muestra en pesos colombianos. Los precios en dólares se convierten con la TRM del día (datos abiertos del Gobierno) y otras monedas con ExchangeRate-API.

No incluye pagos ni carrito: la compra se hace en la página de la tienda.

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Frontend | HTML5, CSS3 y JavaScript sin frameworks (mobile-first, WCAG 2.1 AA) |
| Backend | Java 21, Maven, Spring Boot 3 |
| Seguridad | Spring Security (sesión, BCrypt, roles USUARIO y ADMINISTRADOR, CSRF) |
| Chatbot | Spring AI 1.0 con Gemini (endpoint compatible con OpenAI) |
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
│       │   ├── dto/               Objetos de entrada y respuesta JSON
│       │   ├── service/           Reglas de negocio (búsqueda, cuentas, perfil, PQR, tasas...)
│       │   │   └── mvp/           Estrategia para elegir el producto MVP
│       │   ├── domain/            Clases del dominio (Usuario, Pqr, Oferta, ...)
│       │   ├── validacion/        Validación de campos y política de contraseñas
│       │   ├── seguridad/         Usuario autenticado, bloqueo por intentos y respuestas 401/403
│       │   ├── adapter/           Un adaptador por tienda y fuentes de tasas de cambio
│       │   ├── asistente/         Proveedor de IA con Spring AI y sus herramientas
│       │   ├── dao/               Interfaces DAO y sus implementaciones JDBC
│       │   ├── config/            Configuración, seguridad, CORS y tareas programadas
│       │   └── exception/         Excepciones propias de cada capa
│       ├── main/resources/
│       │   └── application.yml
│       └── test/                  Pruebas unitarias y de integración
├── frontend/                      Interfaz web accesible (se sirve desde el backend)
│   ├── index.html                 Ingreso y registro
│   ├── usuario.html               Interfaz de usuario
│   ├── admin.html                 Interfaz de administrador
│   ├── css/
│   └── js/comun, js/ingreso, js/usuario, js/admin
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

1. Copie `.env.example` como `.env`, cambie la clave de la base de datos y complete `ADMIN_CORREO`, `ADMIN_CLAVE` (cuenta de administrador que se crea al arrancar) y `ASISTENTE_API_KEY` (clave de Gemini, gratis en <https://aistudio.google.com/apikey>).
2. Ejecute `docker compose up --build`.
3. Abra <http://localhost:8080>. La documentación de la API está en <http://localhost:8080/api/docs>.

**Sin Docker:** instale PostgreSQL 16, cree la base `marketradar` y ejecute desde la carpeta `backend`:

```bash
mvn spring-boot:run
```

**Pruebas:** `mvn -f backend/pom.xml verify`. La prueba de los DAO usa Testcontainers y se omite si Docker no está disponible.

## API REST

Las peticiones que cambian datos deben enviar la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`.

| Método | Ruta | Acceso | Función |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/registro` | Público | Crea una cuenta de usuario e inicia sesión |
| POST | `/api/v1/auth/ingreso` | Público | Inicia sesión (bloqueo de 15 min tras 5 intentos fallidos) |
| POST | `/api/v1/auth/salida` | Con sesión | Cierra la sesión |
| GET | `/api/v1/auth/sesion` | Público | Persona con sesión y su interfaz (204 si no hay sesión) |
| GET | `/api/v1/busqueda?q=&orden=&marketplaces=&pagina=&tamano=` | Usuario | Busca en las tiendas activas y devuelve el producto MVP |
| GET | `/api/v1/ofertas/{id}` y `/{id}/historial` | Usuario | Detalle e historial de precios |
| POST | `/api/v1/asistente/mensajes` | Usuario | Pregunta al chatbot |
| GET | `/api/v1/usuario/avisos` | Usuario | Avisos del inicio |
| GET, PUT | `/api/v1/usuario/perfil` | Usuario | Consulta y guarda el perfil |
| PUT | `/api/v1/usuario/perfil/clave` | Usuario | Cambia la contraseña |
| GET, DELETE | `/api/v1/usuario/historial` | Usuario | Historial de búsquedas |
| GET, POST | `/api/v1/usuario/pqr` | Usuario | Lista y radica PQR |
| GET | `/api/v1/admin/resumen` | Administrador | Cifras del panel |
| GET | `/api/v1/admin/pqr?estado=` | Administrador | PQR con nombre y correo de quien la envió |
| GET | `/api/v1/admin/pqr/{id}` | Administrador | Abre una PQR (queda en revisión) |
| POST | `/api/v1/admin/pqr/{id}/respuesta` | Administrador | Responde una PQR |
| GET | `/api/v1/tasas` | Con sesión | Tasas de cambio vigentes |
| GET | `/api/v1/marketplaces`, `/api/v1/salud` | Público | Tiendas y estado del servicio |

## Reglas de negocio

- Contraseña de 8 a 64 caracteres con minúscula, mayúscula, número y carácter especial, sin espacios y sin contener el correo. Se guarda solo su hash BCrypt.
- El correo es único y no se puede cambiar. El documento (CC, CE o pasaporte) es único entre cuentas.
- Solo mayores de 18 años. Celular colombiano de 10 dígitos que empieza por 3. Nombres y ciudad solo con letras.
- Quien se registra queda con rol USUARIO; el administrador se crea con variables de entorno.
- PQR: asunto de 5 a 120 caracteres, descripción de 20 a 2000, máximo 5 abiertas por persona. Pasa de *Recibida* a *En revisión* cuando el administrador la abre y a *Respondida* cuando la contesta; no se responde dos veces.
- Producto MVP: 60 % precio total, 30 % reseñas (promedio bayesiano, para que pocas opiniones perfectas no ganen) y 10 % envío; los usados pierden 10 %. Los pesos se cambian en `application.yml` y deben sumar 1.
- Tasas: se actualizan al arrancar y todos los días a las 6:00 a. m.; si las fuentes fallan se usa la última guardada y, si no hay, `TASA_USD_COP`.

## Estado actual

- [x] Estructura en 3 capas, modelo de datos, DAO JDBC y migraciones.
- [x] Servicio de búsqueda en paralelo con caché, timeout de 4 s y advertencias por tienda.
- [x] Ingreso con roles, registro con contraseña segura y validación de todos los campos.
- [x] Interfaz de usuario (inicio, buscar, historial, perfil, PQR, tutorial y asistente) e interfaz de administrador.
- [x] Producto MVP con enlace directo, conversión a COP con la TRM y chatbot con Spring AI.
- [ ] Adaptador de AliExpress (pendiente de verificar la API de afiliados).
- [ ] Adaptador de Shopify (pendiente: Catalog API o Storefront MCP).
- [x] Adaptador de Mercado Libre (se activa con sus credenciales; ver [docs/integraciones/mercadolibre.md](docs/integraciones/mercadolibre.md)).

Mientras no haya un adaptador activo, la búsqueda responde "Por ahora no hay tiendas disponibles" y el producto MVP no aparece.

Las decisiones abiertas están en [docs/decisiones-pendientes.md](docs/decisiones-pendientes.md) y el reparto de tareas en [docs/tareas.md](docs/tareas.md).

## Cómo colaborar

Lea [CONTRIBUTING.md](CONTRIBUTING.md) antes de su primer cambio. Resumen: nunca se hace force push, cada funcionalidad va en su propia rama y entra a `main` por pull request revisado por otro integrante.
