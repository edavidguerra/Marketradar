# Cómo se elige y se explica la recomendación

Este documento reúne las reglas que usan la página, el tutorial y el asistente para decir cuál es la mejor opción de
compra, qué precio se muestra y a dónde lleva cada botón. El texto que ve la persona en "¿Cómo se calcula la
recomendación?" sale de `PoliticaRecomendacion.explicar` (endpoint `GET /api/v1/busqueda/como-recomendamos`), y el
asistente lo lee con la herramienta `explicarPuntaje`, así que los tres dicen lo mismo y con los pesos que de verdad usa
el servidor.

## 1. Lo que pidió la persona

`RequisitosBusqueda` lee de lo que escribió la persona:

| Requisito | Ejemplo | Cómo se revisa en el título de la oferta |
|---|---|---|
| Modelo | "iphone 13", "ps5", "galaxy s24" | El mismo número con las mismas letras. Si el título trae otro número de la misma forma ("iPhone 14"), no cumple. "PlayStation 5" cuando se escribió "ps5" queda por confirmar. |
| Versión | "pro", "mini", "pro max", "ultra", "plus" | Las palabras de versión que van justo después del modelo deben ser las mismas: un "iPhone 13 Pro" no es un "iPhone 13" y un "Pro Max" no es un "Pro". |
| Capacidad | "128 GB", "1 TB" | Otra capacidad no cumple; si el título no la dice, queda por confirmar. |
| Condición | "nuevo", "usado", "reacondicionado", "de exhibición" | Se lee de lo que informa la tienda (`second_hand_condition`, etiquetas y título). Reacondicionado y de exhibición nunca son nuevos. Si la tienda no dice nada, queda por confirmar: no se da por nuevo. |
| Combos | "combo", "kit", "paquete" | Un combo con otros productos no cumple, salvo que la persona lo haya pedido. |

Las palabras de versión son de uso general en electrónica, no de una marca ni de los ejemplos de la auditoría.

Cada oferta queda en uno de tres grupos:

- **Cumple**: va en la lista principal y puede ser la recomendación.
- **Falta confirmar**: se muestra aparte, en "Parecidas, pero no son lo que pidió", con lo que falta confirmar.
- **No cumple**: también aparte, con el motivo ("Es la versión Pro, no la que pidió", "Es de 256 GB, no de 128 GB",
  "Es reacondicionado, no nuevo").

Si la búsqueda se corrigió ("ifone 13 de 128 gb nuevo" se busca como "iphone 13"), los requisitos salen de lo que
escribió la persona y de la búsqueda corregida juntos (`RequisitosBusqueda.combinar`). La página manda el texto
original (`original`) al pasar de página o filtrar por precio, así que la capacidad y la condición no se pierden. La
búsqueda exacta ("Buscar tal como lo escribió") también los exige.

La capacidad y la condición tampoco cuentan para `FiltroRelevancia`: una oferta de otra capacidad no se esconde, se
muestra aparte diciendo qué le falta.

## 2. Precio, envío y total

| Estado del envío | Cuándo | Qué se suma | ¿Total confirmado? |
|---|---|---|---|
| Gratis | "Envío gratis", o gratis con un mínimo que el precio ya alcanza | 0 | Sí |
| Conocido | "Envío: $ 12.900" en la misma moneda del precio | El valor | Sí |
| Estimado | "Envío desde $ 9.900", "aprox." | El valor, como mínimo | No |
| Desconocido | No dice nada, gratis con un mínimo que no se alcanza, otra moneda o texto sin valor | Nada | No |

Un envío que no se informa **nunca vale cero**. El precio que se muestra es el **costo conocido** (precio más el envío
gratis, conocido o estimado). Al lado dice "en total" solo si el total está confirmado; si no, "más envío", "con envío
aproximado" o "más impuestos", y debajo qué le falta ("No es el precio total: no incluye el envío, que la tienda no
informa").

El total tampoco se confirma cuando la tienda vende desde el exterior (puede cobrar impuestos de importación que no se
conocen) ni cuando el precio tiene una alerta.

**Filtro de precio:** compara el costo conocido. Si alguna oferta del rango no tiene el total confirmado, la página lo
dice arriba ("3 de estas ofertas no tienen el precio total confirmado...").

**"Precio más bajo":** se marca la oferta con el costo conocido más bajo entre las que cumplen y tienen el precio
verificado. Si su total no está confirmado, la etiqueta dice "★ Precio más bajo, sin el total confirmado".

## 3. Precios que no se pueden usar para recomendar

Un precio con alerta se muestra con un recuadro rojo, pero no se recomienda ni se usa para decir cuál es más barata:

- "Desde", por cuotas o por mes: no es el precio completo.
- El número del texto de Google (`price`) no coincide con el que leyó SerpApi (`extracted_price`) en más de 1 %.
- Es más de 4 veces o menos de la cuarta parte de la mediana de las ofertas que cumplen o están por confirmar (si hay al
  menos 3). Así se marcan precios como $ 359.718.000 para un celular.
- Está en una moneda que no se reconoce ("A$", "¥", "S/"), o no hay tasa para ella: esa oferta no se muestra y queda en
  el registro.

## 4. Moneda

Cada precio convertido guarda su rastro: el precio original y su texto ("US$ 699,00"), la moneda, la tasa, la fuente
de la tasa y la fecha desde la que rige. La tarjeta lo muestra: "Precio en la tienda: US$ 699,00. Lo pasamos a pesos con
$ 4.160 por dólar (TRM Superintendencia Financiera (datos.gov.co), vigente desde el 6 de noviembre de 2026)". Ya no se
dice "TRM del día", porque la TRM más reciente puede ser de días anteriores (fines de semana y festivos) o venir de la
fuente de respaldo.

Una oferta se convierte una sola vez: `OfertaNormalizada.convertida` falla si ya se había convertido, y el adaptador
salta las que ya están en pesos. El envío se convierte con la misma tasa que el precio.

## 5. Enlaces

`EnlaceCompra` clasifica el enlace y el botón dice a dónde lleva:

| Enlace | Botón |
|---|---|
| Página de la tienda | "Ver la oferta en falabella.com.co" |
| Redirección de Google a la tienda (`google.com/url?url=`) | "Ver la oferta en" y el dominio de la tienda |
| Ficha de Google Shopping | "Ver las tiendas en Google Shopping" |
| Búsqueda en Google (lo que más llega con `gl=co`) | "Buscar esta oferta en Google" |

Antes de abrir, una ventana explica a dónde va. Para una búsqueda en Google dice que Google no dio el enlace de la
tienda y que hay que revisar que sea el mismo producto y precio. Solo se aceptan direcciones `https` con nombre de
servidor (sin IP ni `localhost`), sin usuario o clave antes del servidor y de máximo 2.048 caracteres, en el servidor
(`EnlaceCompra.esSeguro`) y en la página (`dominioSeguro`).

## 6. Opiniones

Las opiniones de Google Shopping son **del producto**, reunidas por Google de varias tiendas; no son opiniones sobre la
tienda que lo vende. La página, las razones de la recomendación y el asistente lo dicen así.

- **Procedencia** (`ServicioResenas.procedencia`): se compara el producto al que Google asocia las opiniones con el
  título de la oferta. Mismo modelo y versión: verificadas (pueden incluir otras capacidades o colores). Otro modelo o
  versión: rechazadas, no se muestran y se dice por qué. Sin datos para comparar: se muestran con la advertencia.
- **Repetidas**: la misma opinión (mismo autor y texto) publicada en dos sitios se muestra una vez.
- **Conteos**: Google da el total, la distribución por estrellas y unas pocas opiniones escritas por separado. Si no
  cuadran, se explica ("Se muestran 3 de las 1.284 opiniones").
- Cada opinión dice dónde se publicó, o "sitio no informado".
- La traducción sigue igual: solo al pulsar "Traducir".

## 7. Puntaje de conveniencia

Solo compiten las ofertas que **cumplen** todo lo pedido y tienen el **precio verificado**. Si no queda ninguna, no hay
recomendación y la página dice por qué ("Ninguna oferta confirma todo lo que usted pidió (modelo iphone 13, 512 GB,
nuevo), así que no recomendamos ninguna").

```
puntaje = (0,6 × precio + 0,3 × opiniones + 0,1 × envío) × 0,9 si no es nuevo confirmado × 0,9 si vende del exterior
```

- **Precio**: costo más bajo ÷ costo de la oferta. A una oferta con envío desconocido se le suma, solo para comparar,
  el envío más caro conocido de las demás: así no gana por no informar.
- **Opiniones**: promedio bayesiano, `(n × estrellas + 20 × 3) ÷ (n + 20)`, dividido entre 5. Sin opiniones cuenta 3 de
  5, lo mismo que muchas opiniones de 3 estrellas: ni ventaja ni castigo.
- **Envío**: gratis 1; con valor, 1 − envío ÷ total; estimado, desconocido o del exterior, 0.
- Un dato desconocido nunca sube el puntaje: hay pruebas que lo comparan con el mismo producto con el dato conocido
  (`SeleccionPonderadaMvpTest`).

El puntaje va de 0 a 100, es relativo a las ofertas comparadas (filtrar por precio lo vuelve a calcular) y **no es una
probabilidad**. Aparte del puntaje se da la **confianza**, que dice cuánto faltó saber de la oferta recomendada: envío,
impuestos de importación, desde dónde vende, opiniones (o más opiniones) y condición. Alta si no faltó nada, media si
faltó una cosa, baja si faltaron dos o más.

Los pesos (60/30/10) y las 20 opiniones mínimas no cambiaron; se configuran en `application.yml`. Las dos rebajas del
10 % están justificadas en [decisiones.md](decisiones.md) (decisión 17).

## 8. Avisos del inicio

- "Su última búsqueda" usa la búsqueda tal como la escribió la persona y elige la oferta guardada más barata que sí es
  el producto: sin cajas, fundas ni accesorios, del mismo modelo, versión, capacidad y condición, y sin precios atípicos.
  Si el envío no se conoce, dice "más envío".
- "¡Bajó de precio!" solo compara precios de la misma oferta, en la misma moneda, la misma tienda y la misma condición
  (migración V12: `historial_precio.vendedor` y `historial_precio.condicion`). En Google Shopping una misma ficha cambia
  de tienda; antes eso se contaba como una baja.
- El botón del aviso dice a dónde lleva, igual que en las tarjetas.

## 9. Pruebas

`RequisitosBusquedaTest`, `CondicionProductoTest`, `EnlaceCompraTest`, `OfertaNormalizadaTest`, `OfertaDtoTest`,
`AdaptadorGoogleShoppingTest` (envío, monedas, alertas, condición), `AdaptadorMarketplaceBaseTest` (tasa con fuente y
fecha, sin doble conversión, sin tasa), `ServicioBusquedaTest` (iPhone 13 128 GB nuevo, nada cumple, precio atípico,
filtro con envíos incompletos, texto original), `SeleccionPonderadaMvpTest` (datos desconocidos, abstención,
confianza), `PoliticaRecomendacionTest`, `ServicioResenasTest` y `ResenasDtoTest` (procedencia, repetidas, conteos),
`ServicioAvisosTest` y `OfertaDaoJdbcTest` (baja de precio en la misma tienda y condición).
