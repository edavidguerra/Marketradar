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
3. **Si no hay resultados y la búsqueda tiene varias palabras**, prueba con la palabra principal: "licuadora oster roja
   10 velocidades" muestra licuadoras y avisa "No encontramos ... exacto".

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

## 3. Enlaces de compra seguros

- El adaptador de Google Shopping solo guarda enlaces `https` con dominio válido (`AdaptadorGoogleShopping.esSeguro`).
  Prefiere el enlace directo de la tienda y, si no lo hay, abre el producto en Google Shopping.
- En la página, `enlaceTienda` no muestra botón si el enlace no es `https`.
- Antes de salir, una ventana dice a qué dominio va la persona ("Va a abrir www.exito.com (Éxito) en otra pestaña, con
  conexión segura") y le recuerda que Marketradar nunca le pide datos bancarios.

## Pruebas

`ServicioBusquedaFlexibleTest` cubre la corrección directa, las frases, el intento con la palabra principal y la búsqueda
exacta. `ServicioSugerenciasTest` cubre los prefijos, las tildes, los errores de escritura, la segunda palabra, el mínimo de
letras, el orden por popularidad, las correcciones (licuadorra, oya arrosera, conputador portatil), que no corrija
palabras bien escritas, que no confíe en términos populares mal escritos y que siga funcionando si la base de datos
falla.
