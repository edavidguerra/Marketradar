# Asistente con Claude

El chatbot de Marketradar usa **Claude**, el modelo de IA de Anthropic, con los créditos de la cuenta del equipo. El código está en `backend/src/main/java/co/edu/unicesar/marketradar/asistente/ProveedorIAClaude.java` y usa el SDK oficial de Anthropic para Java (`com.anthropic:anthropic-java`).

## Cómo funciona

1. La persona escribe una pregunta en el botón "¿Necesita ayuda?".
2. `ServicioAsistente` revisa la pregunta (no vacía, máximo 500 letras) y le agrega las instrucciones del asistente y los últimos 10 mensajes.
3. `ProveedorIAClaude` envía todo a Claude junto con dos herramientas:
   - `buscarMejorOpcion`: busca el producto en las tiendas y devuelve la mejor opción y las más baratas.
   - `valorDelDolar`: devuelve la TRM del día.
4. Si Claude pide una herramienta, Marketradar la ejecuta, le devuelve el resultado y Claude responde con precios reales y enlaces directos. Se permiten máximo 4 vueltas por pregunta para no gastar créditos de más.
5. Si Claude no responde o la clave está mal, la persona ve un mensaje amable y el resto de la página sigue funcionando.

## Cómo sacar la clave

1. Entrar a <https://console.anthropic.com> con la cuenta que tiene los créditos.
2. Menú **API Keys** > **Create Key**. Ponerle de nombre `marketradar`.
3. Copiar la clave (empieza por `sk-ant-`). Solo se muestra una vez.
4. Recomendado: en **Limits** poner un límite de gasto mensual, por ejemplo 10 USD, para que nadie gaste todos los créditos por error.

La clave va **solo** en el `.env` o en las variables de entorno de Render. Nunca en el código ni en GitHub.

## Variables

| Variable | Valor | Para qué |
|---|---|---|
| `ASISTENTE_API_KEY` | La clave `sk-ant-...` | Sin ella el chatbot responde "El asistente todavía no está disponible" |
| `ASISTENTE_MODELO` | `claude-opus-5-5` (por defecto) | Modelo de Claude que se usa |
| `ASISTENTE_PROVEEDOR` | `claude` (por defecto) | `ninguno` apaga el chatbot |

## Cuánto gasta

Cada pregunta hace una o dos llamadas a Claude. Con `claude-opus-5-5` (4 USD por millón de tokens de entrada y 20 USD por millón de salida) una pregunta cuesta alrededor de 2 a 3 centavos de dólar, así que 50 USD alcanzan para unas 2.000 preguntas. Es un cálculo aproximado: el gasto real se ve en la consola de Anthropic, en **Usage**.

Si el equipo quiere gastar menos, puede cambiar `ASISTENTE_MODELO` a `claude-haiku-5-5`, que cuesta mucho menos por pregunta pero razona menos. No hay que tocar el código.

## Pruebas

`ProveedorIAClaudeTest` prueba el proveedor contra un servidor falso que responde como la API de Anthropic: conversación normal, uso de cada herramienta, negativa de Claude, límite de vueltas y error de la API. No gasta créditos.
