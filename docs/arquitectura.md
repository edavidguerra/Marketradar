# Arquitectura de Marketradar

## Flujo de una búsqueda

```text
Navegador -> GET /api/v1/busqueda?q=...
  -> ControladorBusqueda -> ServicioBusquedaFlexible (quita relleno, corrige errores y, sin resultados, prueba la palabra principal)
    -> ServicioBusqueda
    -> caché Caffeine (si hay resultado vigente, responde)
    -> adaptadores en paralelo con CompletableFuture (timeout 4 s y circuit breaker por tienda)
    -> normalización a OfertaNormalizada y conversión a COP
    -> ServicioPersistenciaOfertas guarda todas las ofertas e historial en una sola consulta (historial solo si el precio cambia)
    -> EstrategiaSeleccionMvp elige la mejor opción de compra entre todas las ofertas
    -> la búsqueda queda en el historial de la persona
  -> respuesta JSON con el producto MVP y las ofertas ordenadas, más las advertencias de las tiendas que fallaron
```

Mientras la persona escribe, el navegador pide `GET /api/v1/busqueda/sugerencias?q=...`. Esas sugerencias salen del
vocabulario de Marketradar y de lo que más se busca, no de las tiendas, así que no gastan consultas de SerpApi. Ver
[busqueda-flexible.md](busqueda-flexible.md).

## Velocidad

Lo que hace que una búsqueda responda rápido, de lo más a lo menos importante:

| Qué | Cómo | Efecto |
| --- | --- | --- |
| Caché de búsquedas | Caffeine guarda cada resultado 60 minutos (`BUSQUEDA_CACHE_MINUTOS`) | Repetir una búsqueda responde en milisegundos y no gasta consultas de SerpApi. |
| Guardar ofertas en lote | `OfertaDao.guardarLote` guarda todas las ofertas de una búsqueda y su historial de precio en **una sola consulta** (`INSERT ... ON CONFLICT` dentro de un `WITH`). Antes eran 2 o 3 consultas por oferta | Con 20 ofertas se pasa de unas 50 idas y vueltas a la base de datos a una. |
| Marketplaces en memoria | `ServicioPersistenciaOfertas` recuerda el id de cada tienda | No se consulta la tabla `marketplace` en cada búsqueda. |
| Archivos comprimidos | `server.compression` comprime JSON, HTML, CSS, JS y SVG | La hoja de estilos pasa de unos 40 KB a unos 9 KB. |
| Archivos revalidados | CSS, JS e imágenes usan `Cache-Control: no-cache` con `Last-Modified`: el navegador pregunta si cambiaron y, si no, recibe un 304 vacío. Las respuestas de `/api/` siguen con `no-store` | Cambiar de página no vuelve a descargar todo, y nunca se ve una versión vieja después de publicar. |

Lo que no depende del código: SerpApi tarda de 2 a 5 segundos en la primera búsqueda de un producto, y el plan gratuito
de Render duerme la web tras 15 minutos sin visitas (ver [despliegue.md](despliegue.md#límites-del-plan-gratuito)).

## Seguridad y roles

```text
Navegador -> Spring Security (sesión HTTP, CSRF con cookie XSRF-TOKEN)
  /api/v1/auth/**      público: registro, ingreso, sesión
  /api/v1/usuario/**, busqueda, ofertas, asistente   rol USUARIO
  /api/v1/admin/**     rol ADMINISTRADOR
```

`ServicioCuentas` aplica la política de contraseñas (`PoliticaContrasena`), cifra con BCrypt y bloquea 15 minutos tras 5 intentos fallidos (`ProteccionIntentosIngreso`). El frontend tiene tres páginas: `index.html` (ingreso y registro), `usuario.html` y `admin.html`; cada una verifica la sesión y lleva a cada rol a la suya.

## Chatbot con Claude

`ServicioAsistente` valida la pregunta, agrega las instrucciones y los últimos 10 mensajes, y llama a `ProveedorIA`. `ProveedorIAClaude` usa el SDK oficial de Anthropic para Java (`anthropic-java`) y le ofrece a Claude las herramientas de `HerramientasAsistente` (`buscarMejorOpcion` y `valorDelDolar`) para que responda con precios reales y enlaces directos en vez de inventarlos. Cuando Claude pide una herramienta, la clase la ejecuta, le devuelve el resultado y repite hasta la respuesta final (máximo 4 vueltas por pregunta). Si el proveedor falla, la persona recibe un mensaje amable.

## Tasas de cambio

`ServicioTasasCambio` implementa `ProveedorTasas` y consulta las `FuenteTasaCambio` en orden: `FuenteTrmBancoRepublica` (TRM oficial del dólar, datos.gov.co) y `FuenteTasasInternacionales` (otras monedas). Guarda cada tasa en `tasa_cambio` y la usa `ConversorMoneda` en los adaptadores.

## Capas y paquetes

| Capa | Paquetes | Responsabilidad |
| --- | --- | --- |
| Presentación | `controller`, `dto`, `frontend/` | Recibir peticiones HTTP, validar formato y responder JSON |
| Lógica de negocio | `service`, `service.mvp`, `domain`, `validacion`, `seguridad`, `adapter`, `asistente` | Reglas de negocio, validaciones, integración con tiendas, tasas y chatbot |
| Acceso a datos | `dao`, `dao.jdbc`, `config` | CRUD con JDBC, conexión y configuración |

## Conceptos de la asignatura en el código

| Concepto | Dónde |
| --- | --- |
| Encapsulamiento | Atributos privados con getters y setters validados en `domain` (por ejemplo `Oferta.setPrecioActual`) |
| Herencia | `AdaptadorAliExpress`, `AdaptadorShopify`, `AdaptadorMercadoLibre` y `AdaptadorGoogleShopping` extienden `AdaptadorMarketplaceBase`; los DAO extienden `DaoJdbcBase` |
| Polimorfismo | `ServicioBusqueda` trata a todos los adaptadores como `AdaptadorMarketplace` |
| Interfaces | `AdaptadorMarketplace`, `Dao<T>`, `ProveedorIA`, `PoliticaContrasena`, `EstrategiaSeleccionMvp`, `FuenteTasaCambio` |
| Composición | `Oferta` contiene su lista de `HistorialPrecio` |
| Agregación | `Producto` agrupa varias `Oferta` |
| ArrayList | Listas de ofertas, advertencias y conversación del asistente |
| HashMap | Registro de adaptadores por código en `ServicioBusqueda`, tasas en `ConversorMoneda` |
| Excepciones propias | `AccesoDatosException`, `MarketplaceException`, `ValidacionException`, `RecursoNoEncontradoException`, `CredencialesInvalidasException` |
| Enumeraciones | `Rol`, `TipoDocumento`, `TipoPqr`, `EstadoPqr` |
| Patrón Builder | `OfertaNormalizada.builder()` |
| Patrón Strategy | `EstrategiaSeleccionMvp` con `SeleccionPonderadaMvp` |
| Patrón DAO | `dao` (interfaces) y `dao.jdbc` (implementaciones con try-with-resources) |

## Principios GRASP y SOLID

| Principio | Clase | Razón |
| --- | --- | --- |
| Controller (GRASP) | `ControladorBusqueda` | Recibe la petición y delega en el servicio sin lógica de negocio |
| Creator (GRASP) | Cada adaptador | Crea las `OfertaNormalizada` porque tiene los datos de la respuesta de su API |
| Information Expert (GRASP) | `Oferta.calcularPrecioTotal`, `Pqr.responder` | Cada clase aplica las reglas de sus propios datos |
| Low Coupling (GRASP) | `ServicioBusqueda` | Depende de la interfaz `AdaptadorMarketplace` |
| High Cohesion (GRASP) | Cada DAO | Trabaja con una sola tabla |
| SRP | `DaoJdbcBase`, `ServicioPersistenciaOfertas` | Acceso a datos separado de la lógica de búsqueda |
| OCP | `AdaptadorMarketplace`, `EstrategiaSeleccionMvp`, `FuenteTasaCambio` | Agregar una tienda, un criterio de MVP o una fuente de tasas es crear una clase nueva |
| ISP | `TasaCambioDao`, `ProveedorTasas` | Interfaces pequeñas con solo lo que usa cada cliente |
| LSP | `AdaptadorMarketplaceBase` | Cualquier adaptador sustituye a la base sin alterar al servicio |
| DIP | `ServicioBusqueda`, `ServicioAsistente`, `ServicioCuentas`, `ConversorMoneda` | Dependen de abstracciones (`AdaptadorMarketplace`, `ProveedorIA`, `PoliticaContrasena`, `ProveedorTasas`) |

## Agrupación de ofertas en `producto`

Propuesta pendiente de aprobación: normalizar el título (minúsculas, sin tildes ni signos) y agrupar ofertas cuyo título normalizado coincida en marca y modelo. Límite conocido: dos tiendas pueden describir el mismo producto con palabras distintas y quedar en grupos separados. Por ahora `oferta.producto_id` queda vacío.
