# Búsqueda con IA (Gemini)

Google Shopping trae de todo: con "iphone 15" llegan forros de $ 30.000, vidrios templados e iPhone 11. Los filtros
propios (`FiltroRelevancia`, `FiltroComplementos`) ayudan, pero no entienden el producto. Por eso la búsqueda se apoya
en Gemini, el modelo de IA de Google, con su plan gratis de Google AI Studio.

## Qué hace

`ServicioIABusqueda` hace dos consultas a Gemini por cada búsqueda nueva:

1. **Antes de buscar** entiende lo que escribió la persona y propone la búsqueda para Google Shopping:
   "quiero un ps5 pa mi nieto" se busca como "playstation 5". Corrige la ortografía, quita el relleno y conserva la
   marca, el modelo y los números. La página dice "Le mostramos resultados de playstation 5" y ofrece buscar
   exactamente lo que se escribió.
2. **Después de buscar** recibe la lista numerada de productos (título, tienda, precio total con envío, si el envío
   es gratis, está incluido o sin confirmar, y desde dónde vende la tienda) y responde en JSON:
   - `exactos`: los que sí son lo buscado (un forro no es un iPhone 15, una PlayStation 4 no es una PlayStation 5);
   - `parecidos`: versiones cercanas (iPhone 15 Pro), que se muestran solo si hay menos de 3 exactos;
   - `resumen`: de 2 a 4 frases, como el resumen con IA de Google: rango de precios totales, el más barato, en qué
     tienda y desde dónde vende. Si la tienda es del exterior avisa que puede tardar y cobrar impuestos, y nombra
     también una opción en Colombia. Se muestra arriba de la recomendación, con el aviso "Lo escribe una IA a partir
     de los precios encontrados y puede equivocarse". Si el resumen nombra un precio ("$ 49.636") que no es de
     ninguno de los productos que se muestran, se descarta y no aparece.

La lista, el resumen y la recomendación quedan en la caché de búsquedas una hora, así que repetir la búsqueda no
vuelve a gastar consultas. Las búsquedas propuestas se recuerdan 24 horas.

Si Gemini no está configurado, se acabó la cuota, tarda más de 10 segundos o responde algo raro, la búsqueda sigue
como antes: corrección con el diccionario propio y filtros propios. La página nunca depende de la IA.

### Traducir reseñas

En **Ver reseñas**, las opiniones escritas en otro idioma que el de la cuenta tienen un botón **Traducir al
español** (o al idioma que la persona eligió al crear la cuenta o en Mi perfil). `DetectorIdioma` adivina el idioma
contando palabras comunes, sin gastar cuota; solo al pulsar el botón `ServicioTraduccion` le pide a Gemini la
traducción (`POST /api/v1/ofertas/{id}/resenas/{indice}/traduccion`). Debajo aparece "Traducido automáticamente del
inglés con inteligencia artificial" y el botón cambia a **Ver el texto original**.

- Solo se traducen reseñas que ya trajo la página, pedidas por su posición: nadie puede usarla para traducir otro texto.
- Cada traducción se guarda 24 horas y cada persona puede pedir 40 por hora.
- Sin clave de Gemini el botón no aparece.

## Cómo se llama

`ClienteGemini` usa la API REST, sin librerías extra:

```
POST https://generativelanguage.googleapis.com/v1beta/models/<modelo>:generateContent
x-goog-api-key: <clave>
```

con `systemInstruction` (las instrucciones), `contents` (los datos) y `generationConfig` con
`responseMimeType: application/json`, `responseSchema` y `temperature: 0`. La clave va en la cabecera, nunca en la
URL, y los errores solo registran el código HTTP. Se ignoran las partes de razonamiento (`thought: true`).

## Cómo sacar la clave gratis

1. Entrar a <https://aistudio.google.com/apikey> con una cuenta de Google.
2. **Create API key** y elegir o crear un proyecto. No pide tarjeta.
3. Copiar la clave (empieza por `AIza`) y ponerla en Render, en **Environment**, como `GEMINI_API_KEY`. No se guarda
   en el repositorio.

## Variables

| Variable | Valor por defecto | Para qué |
| --- | --- | --- |
| `GEMINI_API_KEY` | vacío | Sin clave no se usa la IA. |
| `GEMINI_MODELO` | `gemini-3.5-flash-lite` | Modelo rápido y con cuota gratis amplia. Si Google lo retira, cambiar por el que recomiende <https://ai.google.dev/gemini-api/docs/models>. |
| `GEMINI_HABILITADO` | `true` | `false` apaga la IA sin borrar la clave. |

## Cuota y privacidad

- El plan gratis tiene límite por minuto y por día; se ve en <https://aistudio.google.com/rate-limit>. Cada búsqueda
  nueva usa 2 consultas. Con 429 (cuota agotada) la búsqueda sigue sin IA.
- A Gemini solo se envía lo que se escribió en el buscador, los títulos, tiendas y precios de los productos y, al
  pulsar Traducir, el título y el texto de esa reseña. Nunca el nombre, el correo ni otro dato de la persona.
- En el plan gratis Google puede usar lo enviado para mejorar sus productos. Por eso no se envían datos personales.
