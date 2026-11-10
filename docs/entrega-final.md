# Entrega final de Marketradar

Comparador de precios accesible para adultos mayores. Proyecto de aula de **Programación de Computadores III** y **Bases de Datos**, Universidad Popular del Cesar.

**Equipo:** Ever Guerra (líder), Gustavo Rodriguez, Luis Corzo, Juan Araujo y Jean Mercado.

## Qué hace

1. La persona escribe un producto, por ejemplo "olla arrocera".
2. Marketradar consulta al mismo tiempo AliExpress, Shopify y Mercado Libre, convierte los precios a pesos con la TRM del día y los ordena por precio total.
3. Muestra el **producto MVP**: la mejor compra según precio (60 %), opiniones (30 %) y envío (10 %), con las razones en palabras sencillas y un enlace directo a la tienda.
4. Además: historial de búsquedas, historial de precio por oferta, perfil, PQR, tutorial y un asistente con IA.
5. El administrador ve y responde las PQR.

## Dónde está cada entregable

### Fase 1: problema

| Entregable | Archivo |
|---|---|
| Identificación y formulación del problema | [fase1/identificacion-problema.md](fase1/identificacion-problema.md) |
| Validación de las APIs de las tiendas | [fase1/validacion-apis.md](fase1/validacion-apis.md) |

### Fase 2: arquitectura

| Entregable | Archivo |
|---|---|
| Arquitectura en 3 capas y patrones | [arquitectura.md](arquitectura.md) |
| Diagramas de paquetes y de clases | [fase2/diagramas/](fase2/diagramas/) |
| Mockups | [fase2/mockups/](fase2/mockups/) |

### Fase 3: desarrollo

| Entregable | Archivo |
|---|---|
| Código del backend (Java 21, Spring Boot, JDBC) | [backend/](../backend/) |
| Interfaz web accesible | [frontend/](../frontend/) |
| Integración con cada tienda | [integraciones/](integraciones/) |
| Auditoría de accesibilidad (Lighthouse y axe) | [accesibilidad/auditoria.md](accesibilidad/auditoria.md) |
| Guion de usabilidad y plantilla de resultados | [usabilidad/](usabilidad/) |
| Despliegue en Render | [despliegue.md](despliegue.md) |

### Base de datos

| Entregable | Archivo |
|---|---|
| Migraciones V1 a V10 (11 tablas y 4 vistas) | [base-de-datos/migraciones/](../base-de-datos/migraciones/) |
| Modelo entidad relación | [base-de-datos/docs/mer/](../base-de-datos/docs/mer/) |
| Diccionario de datos | [base-de-datos/docs/diccionario-datos.md](../base-de-datos/docs/diccionario-datos.md) |
| Normalización | [base-de-datos/docs/normalizacion.md](../base-de-datos/docs/normalizacion.md) |
| Datos de demostración, casos de prueba y datos erróneos | [base-de-datos/datos-prueba/](../base-de-datos/datos-prueba/) |
| Consultas de reportes | [base-de-datos/consultas/reportes.sql](../base-de-datos/consultas/reportes.sql) |
| Scripts de creación, permisos y respaldo | [base-de-datos/scripts/](../base-de-datos/scripts/) |

### Fase 4: documentación

| Entregable | Archivo |
|---|---|
| Tabla de tareas con responsables | [tareas.md](tareas.md) |
| Decisiones del equipo | [decisiones.md](decisiones.md) |
| Cómo ejecutar, API REST y reglas de negocio | [README](../README.md) |

## Calidad comprobada

| Qué | Resultado |
|---|---|
| Pruebas automáticas (`mvn -f backend/pom.xml verify`) | 150 pruebas; las 7 de DAO con Testcontainers corren en GitHub Actions |
| Integración continua | Check `pruebas` obligatorio en cada PR hacia `develop` |
| Accesibilidad con axe (WCAG 2.1 AA) | 0 fallas en 14 pantallas |
| Lighthouse en la pantalla de ingreso | 100 en accesibilidad, buenas prácticas, rendimiento y SEO |
| Restricciones de la base de datos | 45 de 45 inserciones erróneas rechazadas |

## Cómo verlo funcionando

- **En internet:** la dirección que entrega Render (ver [despliegue.md](despliegue.md)).
- **En un computador:** `docker compose up --build` y abrir <http://localhost:8080>.
- **Cuentas de demostración** (después de cargar `datos_demo.sql`): `rosa.demo@marketradar.co` (usuario) y `admin.demo@marketradar.co` (administrador), contraseña `Demo2026*`.

## Lo que queda pendiente

| Pendiente | Por qué | Qué hacer |
|---|---|---|
| Resultados de la prueba de usabilidad | Hay que aplicarla con 5 personas mayores | Seguir el [guion](usabilidad/guion-usabilidad.md) y llenar [resultados.md](usabilidad/resultados.md) |
| Claves reales de las tiendas | AliExpress y Shopify piden aprobar una aplicación; Mercado Libre pide autorizar la cuenta | Seguir cada guía en [integraciones/](integraciones/) y ponerlas en Render |
| Guardar resultados de Shopify | Sus términos no permiten guardar ni cachear el catálogo | Decisión del equipo (ver [integraciones/shopify.md](integraciones/shopify.md)) |
| Interfaz de escritorio | La rúbrica menciona NetBeans | Confirmar con el docente que acepta la interfaz web (ver [decisiones.md](decisiones.md), pendiente 1) |
