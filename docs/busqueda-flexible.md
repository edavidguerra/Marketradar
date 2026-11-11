# Búsqueda flexible: sugerencias y corrección de errores

Muchas personas mayores escriben despacio o con errores de ortografía ("oya arrosera", "licuadorra"). Para que igual
encuentren lo que buscan, la página de **Buscar** ayuda de dos formas. Las dos las hace `ServicioSugerencias` y **no
consultan las tiendas**, así que no gastan búsquedas de SerpApi.

## 1. Sugerencias mientras escribe

Desde la segunda letra, y 250 ms después de que la persona deja de escribir, el navegador pide
`GET /api/v1/busqueda/sugerencias?q=<texto>` y muestra hasta 6 opciones debajo del campo.

De dónde salen:

- **Vocabulario fijo:** `backend/src/main/resources/busqueda/vocabulario.txt`, unos 200 productos comunes (computador,
  celular, tensiómetro, olla arrocera...). Para agregar uno, se escribe en una línea nueva con sus tildes.
- **Lo que más se busca:** términos con resultados que buscaron **al menos 2 personas distintas**, para no mostrarle a
  nadie lo que buscó otra persona. Se recargan cada 10 minutos. Si la base de datos falla, se usa solo el vocabulario.

Cómo se ordenan:

| Puntaje | Cuándo |
| --- | --- |
| 3 | El término empieza por lo escrito ("compu" → "computador"). |
| 2 | Una palabra del término empieza por lo escrito ("arro" → "olla arrocera"). |
| 1 | El comienzo se parece aunque tenga errores: 1 letra distinta desde 4 letras, 2 desde 7 ("lisuadora" → "licuadora", "televisr" → "televisor"). |

Con el mismo puntaje van primero los términos más buscados y después los más cortos. Las tildes y mayúsculas no
importan.

### Accesibilidad

El campo sigue el patrón *combobox* de ARIA 1.2 (`frontend/js/usuario/sugerencias.js`):

- Flecha abajo y flecha arriba recorren las opciones, Enter elige y Escape cierra.
- Enter siempre busca: con una sugerencia marcada busca esa, y si no, busca lo escrito, aunque la lista esté abierta.
  En el celular el teclado muestra la tecla "Buscar" (`enterkeyhint="search"`).
- Un lector de pantalla anuncia cuántas sugerencias hay (`#estado-sugerencias`, `role="status"`).
- Cada opción mide al menos 48 px de alto. En alto contraste la opción elegida se ve en amarillo.
- Mientras la lista está abierta, el botón flotante "¿Necesita ayuda?" se oculta para no taparla en el celular.

## 2. Búsqueda que entiende errores, al estilo de Google

`ServicioBusquedaFlexible` decide qué buscar antes de consultar las tiendas:

1. **Quita el relleno** del comienzo y del final: "quiero comprar una olla arrocera por favor", "¿cuánto cuesta un
   tensiómetro?" o "dónde puedo comprar la licuadora" buscan solo el producto. Las palabras del medio no se tocan
   ("olla de presión"). Esto no se le avisa a la persona.
2. **Corrige las palabras mal escritas y busca directamente lo corregido.** "licuadorra" busca "licuadora" y "telebisor
   samsumg" busca "televisor samsung". Arriba de los resultados sale "Le mostramos resultados de **licuadora**" y un
   botón "Buscar "licuadorra" tal como lo escribió", que repite la búsqueda sin corregir (`exacto=true`).
   Si la última palabra quedó a medias y lo escrito es el comienzo de un producto conocido, se completa: "olla arro"
   busca "olla arrocera" y "compu" busca "computador". Una palabra completa ("olla") no se cambia.
3. **Si no hay resultados y la búsqueda tiene varias palabras**, prueba con la palabra principal: "licuadora oster roja
   10 velocidades" muestra licuadoras y avisa "No encontramos ... exacto". No lo hace si una tienda falló o tardó
   demasiado: ese 0 no quiere decir que el producto no exista, así que se muestra el aviso de la tienda.

Cómo se corrige cada palabra de 3 letras o más, sin números, que no esté en el diccionario:

- **Distancia de Levenshtein:** cuántas letras hay que cambiar, quitar o poner. Se permite 1 cambio hasta 6 letras y 2
  desde 7.
- **Cómo suena en español:** antes de comparar se igualan ll/y, v/b, z/s, c suave/s, qu/k, c fuerte/k y se quita la h
  muda. Así "oya" se corrige a "olla" y "arrosera" a "arrocera".

El diccionario tiene los productos de `vocabulario.txt`, los términos populares y `palabras-comunes.txt` (colores,
tamaños, marcas y palabras como "para" o "con"). Las palabras comunes nunca se "corrigen", así "licuadora oster roja"
se busca tal cual. Para que un error frecuente no se vuelva "correcto", un término popular entra al diccionario solo si
se buscó **5 veces o más**.

Solo la búsqueda que se muestra queda en el historial, con el texto corregido. Los intentos quedan en caché; el único
gasto extra de SerpApi es el segundo intento cuando una búsqueda larga no da resultados.

## 3. Primero lo que más se parece a lo buscado

`FiltroRelevancia` cuenta cuántas palabras de la búsqueda trae cada título y muestra primero los que las traen todas.
Con "licuadora oster", "Licuadora Oster 600 W" (2 de 2) queda y "Licuadora Imusa" (1 de 2) sale.

- Los números se comparan exactos: con "playstation 5" no aparece "PlayStation 4".
- Se entienden plurales, tildes, mayúsculas y abreviaturas comunes: "ollas" es "olla", "PS5" es "playstation 5" y "TV" es
  "televisor".
- No cuentan las palabras que no dicen qué producto es ("de", "para", "con"...).
- Si menos de 3 resultados traen todas las palabras, se agregan los que traen una menos, y así. Si ninguno trae ninguna
  palabra, se muestran todos, para no dejar a la persona sin nada.
- Entre los elegidos se respeta el orden de la tienda. Después se aplica el filtro de complementos.
- Por último se quitan los precios de menos de la cuarta parte del precio del medio, siempre que queden al menos 3: con
  "playstation" una consola de $ 80.000 es una PlayStation vieja o un repuesto, no una PlayStation 5.

## 4. Primero el producto, no sus complementos

Quien busca "playstation 5" quiere la consola, pero Google también trae juegos, paquetes de la tienda digital,
controles y fundas. Como son más baratos, quedaban de primeros. `FiltroComplementos` los quita cuando la persona no
escribió esas palabras y quedan al menos 3 resultados del producto en sí. Un resultado es complemento si:

- su título **empieza** por "juego", "funda", "cargador", "control", "repuesto", "tarjeta"... ("Juego PS5 Spider-Man 2");
- trae "DLC", "expansión" o "skins" en cualquier parte;
- dice "para" o "compatible con" seguido del producto ("Control para PlayStation 5");
- o lo vende una tienda de juegos digitales (PlayStation Store, Microsoft Store, Nintendo eShop, Steam).

"Juego de ollas" o "set de cuchillos" no son complementos: ahí "juego" quiere decir varias piezas. Si la persona
busca "juego ps5", se le muestran los juegos. Si casi todo son complementos, se muestra todo.

## 5. Enlaces de compra seguros

- El adaptador de Google Shopping solo guarda enlaces `https` con dominio válido (`AdaptadorGoogleShopping.esSeguro`).
  Prefiere el enlace directo de la tienda y, si no lo hay, abre el producto en Google Shopping.
- En la página, `enlaceTienda` no muestra botón si el enlace no es `https`.
- Antes de salir, una ventana dice a qué dominio va la persona ("Va a abrir www.exito.com (Éxito) en otra pestaña, con
  conexión segura") y le recuerda que Marketradar nunca le pide datos bancarios.

## Pruebas

`FiltroRelevanciaTest` cubre "licuadora oster", "playstation 5" contra "playstation 4", la abreviatura PS5, los plurales y
qué pasa cuando pocos o ningún título traen las palabras. `FiltroComplementosTest` cubre la consola sin juegos, los juegos cuando se piden, el juego de ollas y el caso en que casi
todo son complementos. `ServicioBusquedaFlexibleTest` cubre la corrección directa, las frases, el intento con la palabra principal (y que no se haga cuando una tienda falla) y la búsqueda
exacta. `ServicioSugerenciasTest` cubre los prefijos, las tildes, los errores de escritura, la segunda palabra, el mínimo de
letras, el orden por popularidad, las correcciones (licuadorra, oya arrosera, conputador portatil), que complete palabras a
medias (olla arro), que no corrija
palabras bien escritas, que no confíe en términos populares mal escritos y que siga funcionando si la base de datos
falla.
