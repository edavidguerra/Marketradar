# Búsqueda flexible: sugerencias y "¿Quizás quiso decir?"

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

## 2. "¿Quizás quiso decir...?"

Cuando la persona busca, el controlador revisa el término. Cada palabra de 3 letras o más, sin números, que no esté en
el diccionario se compara con las palabras conocidas:

- **Distancia de Levenshtein:** cuántas letras hay que cambiar, quitar o poner. Se permite 1 cambio hasta 6 letras y 2
  desde 7.
- **Cómo suena en español:** antes de comparar se igualan ll/y, v/b, z/s, c suave/s, qu/k, c fuerte/k y se quita la h
  muda. Así "oya" se corrige a "olla" y "arrosera" a "arrocera".

Si alguna palabra cambió, la respuesta trae `quizas_quiso_decir` (por ejemplo `"olla arrocera"`) y la página muestra un
botón "¿Quizás quiso decir olla arrocera?" que repite la búsqueda con un clic. Solo se revisa en la primera página de
resultados.

Para que un error frecuente no se vuelva "correcto", un término popular entra al diccionario de correcciones solo si se
buscó **5 veces o más**.

## 3. Enlaces de compra seguros

- El adaptador de Google Shopping solo guarda enlaces `https` con dominio válido (`AdaptadorGoogleShopping.esSeguro`).
  Prefiere el enlace directo de la tienda y, si no lo hay, abre el producto en Google Shopping.
- En la página, `enlaceTienda` no muestra botón si el enlace no es `https`.
- Antes de salir, una ventana dice a qué dominio va la persona ("Va a abrir www.exito.com (Éxito) en otra pestaña, con
  conexión segura") y le recuerda que Marketradar nunca le pide datos bancarios.

## Pruebas

`ServicioSugerenciasTest` cubre los prefijos, las tildes, los errores de escritura, la segunda palabra, el mínimo de
letras, el orden por popularidad, las correcciones (licuadorra, oya arrosera, conputador portatil), que no corrija
palabras bien escritas, que no confíe en términos populares mal escritos y que siga funcionando si la base de datos
falla.
