# Diagramas de la Fase 2

Los diagramas se generaron a partir del código de `backend/` y están en PlantUML. Cada uno tiene su fuente (`.puml`) y
su imagen en SVG (para el documento y GitHub) y PNG (para Word).

| Diagrama | Imagen | Fuente | Qué muestra |
| --- | --- | --- | --- |
| Paquetes por capas | [paquetes.svg](paquetes.svg) | [paquetes.puml](paquetes.puml) | Las tres capas (presentación, lógica de negocio y acceso a datos), los paquetes de cada una y sus dependencias. |
| Clases del dominio | [clases-dominio.svg](clases-dominio.svg) | [clases-dominio.puml](clases-dominio.puml) | Las clases del paquete `domain` con sus atributos, métodos principales y relaciones. |
| Adaptadores y estrategias | [clases-adaptadores.svg](clases-adaptadores.svg) | [clases-adaptadores.puml](clases-adaptadores.puml) | Herencia y polimorfismo de los adaptadores de tiendas, las fuentes de tasas de cambio y la selección del producto MVP. |
| Patrón DAO | [clases-dao.svg](clases-dao.svg) | [clases-dao.puml](clases-dao.puml) | La interfaz genérica `Dao<T>`, las interfaces por entidad y sus implementaciones JDBC. |

En los diagramas de clases se omiten los getters y setters para que se lean mejor.

## Relaciones que muestran los diagramas

- **Herencia:** `AdaptadorAliExpress`, `AdaptadorShopify` y `AdaptadorMercadoLibre` extienden `AdaptadorMarketplaceBase`;
  todas las implementaciones JDBC extienden `DaoJdbcBase`.
- **Interfaces y polimorfismo:** `ServicioBusqueda` trabaja con la interfaz `AdaptadorMarketplace` y no conoce a los
  adaptadores concretos; lo mismo ocurre con `EstrategiaSeleccionMvp`, `ProveedorTasas`, `FuenteTasaCambio` y `Dao<T>`.
- **Composición:** `Oferta` contiene su lista de `HistorialPrecio`, que no existe sin la oferta.
- **Agregación:** `Producto` agrupa varias `Oferta`, y `ResultadoBusqueda` agrupa las `OfertaNormalizada` encontradas.
- **Asociación:** un `Usuario` radica varias `Pqr` y realiza varias `Busqueda`; un `Marketplace` publica varias `Oferta`.

## Cómo regenerar las imágenes

Con Java instalado y el archivo `plantuml.jar` (<https://plantuml.com/download>), desde esta carpeta:

```bash
java -jar plantuml.jar -charset UTF-8 -tsvg *.puml
java -jar plantuml.jar -charset UTF-8 -tpng *.puml
```

Los diagramas usan el motor de diseño interno de PlantUML (`!pragma layout smetana`), así que no hace falta instalar
Graphviz. También se pueden abrir con la extensión PlantUML de VS Code.
