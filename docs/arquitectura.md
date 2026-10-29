# Arquitectura de Marketradar

## Flujo de una búsqueda

```text
Navegador -> GET /api/v1/busqueda?q=...
  -> ControladorBusqueda -> ServicioBusqueda
    -> caché Caffeine (si hay resultado vigente, responde)
    -> adaptadores en paralelo con CompletableFuture (timeout 4 s y circuit breaker por tienda)
    -> normalización a OfertaNormalizada y conversión a COP
    -> ServicioPersistenciaOfertas guarda ofertas e historial (solo si el precio cambia)
  -> respuesta JSON ordenada por precio total, con advertencias de las tiendas que fallaron
```

## Capas y paquetes

| Capa | Paquetes | Responsabilidad |
| --- | --- | --- |
| Presentación | `controller`, `dto`, `frontend/` | Recibir peticiones HTTP, validar formato y responder JSON |
| Lógica de negocio | `service`, `domain`, `adapter`, `asistente` | Reglas del comparador, integración con tiendas y chatbot |
| Acceso a datos | `dao`, `dao.jdbc`, `config` | CRUD con JDBC, conexión y configuración |

## Conceptos de la asignatura en el código

| Concepto | Dónde |
| --- | --- |
| Encapsulamiento | Atributos privados con getters y setters validados en `domain` (por ejemplo `Oferta.setPrecioActual`) |
| Herencia | `AdaptadorAliExpress`, `AdaptadorShopify` y `AdaptadorMercadoLibre` extienden `AdaptadorMarketplaceBase`; los DAO extienden `DaoJdbcBase` |
| Polimorfismo | `ServicioBusqueda` trata a todos los adaptadores como `AdaptadorMarketplace` |
| Interfaces | `AdaptadorMarketplace`, `Dao<T>`, `ProveedorIA` |
| Composición | `Oferta` contiene su lista de `HistorialPrecio` |
| Agregación | `Producto` agrupa varias `Oferta` |
| ArrayList | Listas de ofertas, advertencias y conversación del asistente |
| HashMap | Registro de adaptadores por código en `ServicioBusqueda`, tasas en `ConversorMoneda` |
| Excepciones propias | `AccesoDatosException`, `MarketplaceException`, `ValidacionException`, `RecursoNoEncontradoException` |
| Patrón DAO | `dao` (interfaces) y `dao.jdbc` (implementaciones con try-with-resources) |

## Principios GRASP y SOLID

| Principio | Clase | Razón |
| --- | --- | --- |
| Controller (GRASP) | `ControladorBusqueda` | Recibe la petición y delega en el servicio sin lógica de negocio |
| Creator (GRASP) | Cada adaptador | Crea las `OfertaNormalizada` porque tiene los datos de la respuesta de su API |
| Information Expert (GRASP) | `Oferta.calcularPrecioTotal` | Posee el precio y el envío |
| Low Coupling (GRASP) | `ServicioBusqueda` | Depende de la interfaz `AdaptadorMarketplace` |
| High Cohesion (GRASP) | Cada DAO | Trabaja con una sola tabla |
| SRP | `DaoJdbcBase`, `ServicioPersistenciaOfertas` | Acceso a datos separado de la lógica de búsqueda |
| OCP | `AdaptadorMarketplace` | Agregar una tienda es crear una clase nueva, sin tocar `ServicioBusqueda` |
| LSP | `AdaptadorMarketplaceBase` | Cualquier adaptador sustituye a la base sin alterar al servicio |
| DIP | `ServicioBusqueda`, `ServicioAsistente` | Dependen de abstracciones (`AdaptadorMarketplace`, `Dao<T>`, `ProveedorIA`) |

## Agrupación de ofertas en `producto`

Propuesta pendiente de aprobación: normalizar el título (minúsculas, sin tildes ni signos) y agrupar ofertas cuyo título normalizado coincida en marca y modelo. Límite conocido: dos tiendas pueden describir el mismo producto con palabras distintas y quedar en grupos separados. Por ahora `oferta.producto_id` queda vacío.
