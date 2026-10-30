# Fase 1: Identificación y formulación del problema

Proyecto Marketradar, comparador de precios accesible para adultos mayores en Colombia.

Programación de Computadores III (SS462), Universidad Popular del Cesar.
Docente: Ing. Esp. Alfredo Bautista.
Integrantes: Ever Guerra, Gustavo Rodriguez, Luis Corzo, Juan Araujo y Jean Mercado.

## 1. Resumen

Comprar por internet obliga a comparar el mismo producto en varias tiendas, cada una con su diseño, sus filtros y sus
costos de envío. Para una persona mayor con poca experiencia digital esa comparación es lenta, confusa y, muchas veces,
la razón para no comprar o para pagar más de lo necesario. Marketradar es una aplicación web que hace la comparación por
ella: la persona escribe qué producto busca, el sistema consulta las tiendas por medio de sus APIs oficiales, muestra en
pesos colombianos dónde está más barato y la lleva con un solo botón a la página de compra. Toda la interfaz sigue
las pautas de accesibilidad WCAG 2.1 nivel AA, con letra grande, alto contraste, lenguaje sencillo y una sola tarea por
pantalla.

## 2. Introducción

El comercio electrónico se volvió una forma habitual de comprar en Colombia, pero las tiendas en línea están diseñadas
para un usuario joven y con experiencia: muchas opciones en pantalla, ventanas emergentes, carruseles, precios en otras
monedas y costos de envío que aparecen al final. Los adultos mayores son uno de los grupos que más dificultades
encuentra en estos sitios.

Este documento identifica el problema que enfrenta ese grupo al comparar precios en línea, lo formula como una pregunta
que el proyecto debe responder, justifica por qué vale la pena resolverlo y define los objetivos y el alcance de la
solución. La validación de las APIs de cada tienda y la revisión de comparadores existentes, que forman el estado del
arte, se documentan aparte en `validacion-apis.md` (tarea #32).

## 3. Planteamiento del problema

### 3.1 Identificación

Las personas mayores de 60 años en Colombia compran por internet de forma ocasional y se enfrentan a varios obstáculos:

- **Comparar es trabajo manual.** Para saber dónde está más barato un producto hay que abrir cada tienda, buscarlo,
  interpretar sus filtros y anotar precios. Con tres tiendas son tres búsquedas distintas.
- **El precio real no se ve a primera vista.** El envío se suma al final y algunas tiendas muestran precios en dólares,
  por lo que el número que se compara no es el que se paga.
- **Las interfaces no están pensadas para ellas.** Letra pequeña, poco contraste, botones pequeños y juntos, anuncios
  y términos en inglés dificultan la lectura y el uso a quien tiene baja visión, temblor en las manos o poca práctica
  con el mouse o la pantalla táctil.
- **La desconfianza hace desistir.** Un estudio con adultos mayores encontró altos riesgos percibidos tanto en las
  compras físicas como en las compras en línea, y que esos riesgos afectan su intención de compra (Acosta et al., 2022).
  Ante lo que no entienden, muchas personas abandonan la compra o piden ayuda a un familiar, y pierden autonomía.

El resultado es que este grupo paga más de lo necesario, compra menos de lo que podría o depende de otra persona para
hacerlo.

### 3.2 Descripción

Colombia envejece rápido. Según cifras basadas en las proyecciones del DANE, en 2024 había cerca de 7,9 millones de
personas de 60 años o más, alrededor del 15 % de la población; para 2050 serían unos 14,9 millones y para 2070 cerca de
20 millones, más de una tercera parte de los colombianos (36 %) (Fundación Saldarriaga Concha, 2025).

Al mismo tiempo, internet ya es parte de la vida diaria: en 2024 el 79,3 % de las personas de 5 años o más usó internet
(DANE, 2025). Pero las brechas son más evidentes entre los adultos mayores, y entre los adultos que no acceden a
internet el principal motivo es no saber usarlo (44 %) (Ipsos, 2025, con datos de la Encuesta de Calidad de Vida 2023
del DANE). Además, el 93,2 % de quienes no compran en línea dice que prefiere comprar en persona (DANE, 2025). Es decir,
la población que más crece es también la que menos aprovecha las compras por internet.

Sin embargo, las herramientas de comparación de precios existentes, como los buscadores de productos de propósito general, tampoco
están diseñadas para este grupo: muestran muchos resultados a la vez, mezclan anuncios con ofertas y no explican de
forma sencilla cuál es la mejor opción.

Además, varias de las tiendas más usadas no ofrecen una API pública de consulta de precios (Temu y Shein solo tienen
APIs para vendedores), por lo que una solución seria debe limitarse a las tiendas con acceso oficial y no recurrir al
*web scraping*, que viola sus términos de uso.

### 3.3 Formulación

¿Cómo puede una persona mayor de 60 años con poca experiencia digital encontrar, sin ayuda y en pocos minutos, la tienda
en línea donde un producto le cuesta menos, con el precio total en pesos colombianos y un acceso directo y seguro a la
página de compra?

## 4. Justificación

**Social.** Marketradar le devuelve autonomía a un grupo que suele quedar por fuera del comercio electrónico. Una
persona mayor puede comparar precios por sí misma, ahorrar dinero y decidir con información clara. La Ley 1251 de 2008
reconoce el deber de promover la participación y la autonomía de las personas mayores en Colombia.

**Económica.** Comparar el precio total, con envío y convertido a pesos con la TRM del día, evita pagar de más por
desconocer el costo real. La selección del producto MVP pondera también las reseñas y el envío, para que la opción más
barata no sea siempre la de peor calidad.

**Técnica.** El proyecto aplica lo que exige la asignatura: programación orientada a objetos con herencia,
polimorfismo e interfaces, arquitectura en tres capas, patrón DAO con JDBC sobre PostgreSQL, manejo de excepciones,
pruebas automáticas y control de versiones con Git. Integra además APIs reales, resiliencia ante fallas de un tercero y
un asistente de inteligencia artificial.

**Accesibilidad.** La interfaz se construye desde el inicio con las pautas WCAG 2.1 nivel AA, lo que la hace útil
también para personas con baja visión o con dificultades motoras, sin importar su edad.

**Legal y ética.** Solo se usan APIs oficiales y se respetan sus términos de uso y límites. Los datos personales de los
usuarios se tratan según la Ley 1581 de 2012 de protección de datos personales, y las contraseñas se guardan solo como
hash BCrypt.

## 5. Objetivos

### 5.1 Objetivo general

Desarrollar una aplicación web accesible que permita a adultos mayores en Colombia comparar el precio de un producto en
varias tiendas en línea mediante sus APIs oficiales, ver el precio total en pesos colombianos y abrir la mejor oferta en
la tienda original, siguiendo una arquitectura en tres capas con Java y PostgreSQL.

### 5.2 Objetivos específicos

1. Validar qué tiendas ofrecen una API pública u oficial de consulta de precios, con sus requisitos de acceso, límites
   de uso y términos, para definir las fuentes del sistema.
2. Diseñar una arquitectura en tres capas (presentación, lógica de negocio y acceso a datos) con un adaptador por tienda,
   de modo que agregar una tienda nueva no exija modificar el código existente.
3. Implementar la búsqueda en paralelo en las tiendas activas, la conversión de precios a pesos colombianos con la TRM
   del día y la selección de la mejor opción de compra según precio total, reseñas y envío.
4. Persistir en PostgreSQL las ofertas, el historial de precios, las búsquedas, los usuarios y sus PQR mediante el patrón
   DAO con JDBC.
5. Construir una interfaz web que cumpla WCAG 2.1 nivel AA, con ingreso por roles, interfaz de usuario e interfaz de
   administrador.
6. Evaluar la accesibilidad con Lighthouse y axe, y la usabilidad con al menos cinco adultos mayores reales.

## 6. Alcance

### 6.1 Incluye

- Registro e ingreso con roles USUARIO y ADMINISTRADOR, con contraseña segura.
- Búsqueda de un producto en las tiendas con API oficial: AliExpress (API de afiliados), Shopify y Mercado Libre
  Colombia, esta última solo si la prueba de acceso con OAuth funciona.
- Resultados ordenados por precio, con la mejor opción de compra destacada y un botón que abre esa oferta en la tienda.
- Historial de precios de cada oferta e historial de búsquedas de cada persona.
- Conversión a pesos colombianos con la TRM oficial y tasas de otras monedas.
- Perfil de usuario, PQR con número de radicado y su gestión por parte del administrador.
- Tutorial paso a paso y asistente conversacional que consulta precios reales.
- Interfaz web *mobile-first* desde 360 px de ancho, en español de Colombia.

### 6.2 No incluye

- Pagos, carrito de compras ni compras dentro de Marketradar: la compra siempre se hace en la página de la tienda.
- Tiendas sin API pública de consulta de precios, como Temu y Shein.
- *Web scraping* o servicios de terceros que extraigan datos sin autorización.
- Aplicación móvil nativa.

### 6.3 Limitaciones

- La disponibilidad de cada tienda depende de su API, sus límites de uso y la aprobación de las credenciales.
- Los precios son informativos y pueden cambiar en la tienda después de la consulta. La aplicación indica cuándo se
  consultó cada precio.
- Si un programa de afiliados genera comisión por el enlace, la interfaz lo indica de forma visible.

## 7. Bibliografía

- Congreso de Colombia. (2008). *Ley 1251 de 2008, por la cual se dictan normas tendientes a procurar la protección,
  promoción y defensa de los derechos de los adultos mayores*.
- Congreso de Colombia. (2012). *Ley 1581 de 2012, por la cual se dictan disposiciones generales para la protección de
  datos personales*.
- W3C. (2018). *Web Content Accessibility Guidelines (WCAG) 2.1*. <https://www.w3.org/TR/WCAG21/>
- Acosta, S. G., Cavazos Arroyo, J., Mayett Moreno, Y. y Calderón Valencia, C. I. (2022). Factores que influyen en la
  intención de compra física y en línea de adultos mayores en supermercados. *Estudios Gerenciales*, 38(165), 479-492.
  <https://doi.org/10.18046/j.estger.2022.165.5261>
- DANE. (2025). *Boletín técnico: Encuesta de Tecnologías de la Información y las Comunicaciones en Hogares (ENTIC
  Hogares) 2024*. <https://www.dane.gov.co/files/operaciones/ENTIC/bol-ENTICHogares-2024.pdf>
- Fundación Saldarriaga Concha. (2025). *Colombia envejece: las oportunidades de una sociedad longeva*.
  <https://www.saldarriagaconcha.org/wp-content/uploads/2025/02/2025-02-Febrero18_ColombiaEnvejece-LasOportunidadesDeUnaSociedadLongeva.pdf>
- Ipsos Colombia. (2025, 20 de junio). *Más de 31 millones de colombianos acceden a internet, pero persisten brechas en
  su uso*. El Frente. <https://elfrente.com.co/mas-de-31-millones-de-colombianos-acceden-a-internet-pero-persisten-brechas-en-su-uso/>
