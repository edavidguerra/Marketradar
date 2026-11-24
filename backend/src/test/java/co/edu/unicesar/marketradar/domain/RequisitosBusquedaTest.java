package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequisitosBusquedaTest {

    private static final Optional<CondicionProducto> NUEVO = Optional.of(CondicionProducto.NUEVO);
    private static final Optional<CondicionProducto> SIN_DECIR = Optional.empty();

    @Test
    void leeModeloCapacidadYCondicionDeLoQueEscribio() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("iPhone 13 128 GB nuevo");

        assertEquals(List.of("modelo iphone 13", "128 GB", "nuevo"), requisitos.etiquetas());
    }

    @Test
    void unaBusquedaSinDetallesNoExigeNada() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("licuadora");

        assertTrue(requisitos.estaVacio());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Licuadora Oster 10 velocidades", SIN_DECIR).nivel());
    }

    @ParameterizedTest(name = "{0} con «{1}»: {2}")
    @CsvSource(delimiter = '|', value = {
        "iphone 13 128 gb nuevo | Apple iPhone 13 128 GB Azul Nuevo          | CUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 Pro 128 GB                 | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 mini 128 GB                | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 256 GB                     | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 14 128 GB                     | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 Azul                       | SIN_CONFIRMAR",
        "iphone 13 128 gb nuevo | Apple iPhone 13 128 GB Reacondicionado     | INCUMPLE",
        "iphone 13 128 gb nuevo | iPhone 13 128 GB de exhibición             | INCUMPLE",
        "iphone 13 128 gb nuevo | iPhone 13 128GB usado como nuevo           | INCUMPLE",
        "iphone 17 pro          | Apple iPhone 17 Pro 256 GB                 | CUMPLE",
        "iphone 17 pro          | Apple iPhone 17 Pro Max 256 GB             | INCUMPLE",
        "iphone 17 pro          | Apple iPhone 17 256 GB                     | INCUMPLE",
        "iphone 17 pro max      | iPhone 17 Pro Max 512 GB Naranja           | CUMPLE",
        "iphone 17 pro max      | iPhone 17 Pro 512 GB                       | INCUMPLE",
        "galaxy s24 ultra       | Samsung Galaxy S24 Ultra 256 GB            | CUMPLE",
        "galaxy s24 ultra       | Samsung Galaxy S24+ 256 GB                 | INCUMPLE",
        "galaxy s24             | Samsung Galaxy S23 128 GB                  | INCUMPLE",
        "ps5                    | Consola PlayStation 5 Slim                 | CUMPLE",
        "ps5                    | Consola PS5 Slim 1 TB                      | CUMPLE",
        "portatil 1 tb          | Portátil Lenovo 1TB SSD                    | CUMPLE",
        "portatil 1 tb          | Portátil Lenovo 512 GB SSD                 | INCUMPLE",
        "iphone 15              | Combo iPhone 15 128 GB + AirPods           | INCUMPLE",
    })
    void comparaCadaOfertaConLoQuePidio(String busqueda, String titulo, Nivel esperado) {
        Optional<CondicionProducto> condicion = CondicionProducto.detectar(titulo);

        RequisitosBusqueda.Evaluacion evaluacion = RequisitosBusqueda.de(busqueda).evaluar(titulo, condicion);

        assertEquals(esperado, evaluacion.nivel(), evaluacion.motivos().toString());
    }

    @Test
    void siLaTiendaNoDiceLaCondicionNoSeDaPorNuevo() {
        RequisitosBusqueda.Evaluacion evaluacion = RequisitosBusqueda.de("iphone 13 128 gb nuevo")
                .evaluar("Apple iPhone 13 128 GB Azul", SIN_DECIR);

        assertEquals(Nivel.SIN_CONFIRMAR, evaluacion.nivel());
        assertEquals(List.of("La tienda no dice si es nuevo"), evaluacion.motivos());
    }

    @Test
    void cadaMotivoDiceQueLeFalta() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("iphone 13 128 gb nuevo");

        assertEquals(List.of("Es la versión Pro, no la que pidió"),
                requisitos.evaluar("Apple iPhone 13 Pro 128 GB", NUEVO).motivos());
        assertEquals(List.of("Es de 256 GB, no de 128 GB"),
                requisitos.evaluar("Apple iPhone 13 256 GB", NUEVO).motivos());
        assertEquals(List.of("Es reacondicionado, no nuevo"),
                requisitos.evaluar("Apple iPhone 13 128 GB", Optional.of(CondicionProducto.REACONDICIONADO)).motivos());
        assertEquals(List.of("No es la versión Pro"),
                RequisitosBusqueda.de("iphone 17 pro").evaluar("Apple iPhone 17 256 GB", NUEVO).motivos());
    }

    @Test
    void laCorreccionNoBorraLoQuePidioLaPersona() {
        // La corrección quitó "128 gb nuevo" y cambió "ps5" por "playstation 5".
        RequisitosBusqueda requisitos = RequisitosBusqueda.combinar("ps5 825 gb nueva", "playstation 5");

        assertEquals(List.of("modelo playstation 5", "825 GB", "nuevo"), requisitos.etiquetas());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Consola PlayStation 5 825 GB nueva", NUEVO).nivel());
        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Consola PlayStation 5 1 TB", NUEVO).nivel());
    }

    @Test
    void siLaCorreccionCambiaElNumeroSeExigenAmbos() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.combinar("iphone 13 pro", "iphone 14 pro");

        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Apple iPhone 14 Pro 128 GB", NUEVO).nivel());
    }

    @Test
    void quienPideUnComboPuedeVerCombos() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("combo iphone 15 con airpods");

        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Combo iPhone 15 128 GB + AirPods", NUEVO).nivel());
    }

    @Test
    void unNumeroQueEsParteDeOtraCosaNoEsUnModeloDistinto() {
        // "10 velocidades" no compite con el modelo "oster 6640".
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("licuadora oster 6640");

        assertEquals(Nivel.CUMPLE,
                requisitos.evaluar("Licuadora Oster 6640 de 10 velocidades 1.25 litros", SIN_DECIR).nivel());
    }

    // --- RAM y almacenamiento por separado (auditoría, caso "Es de 4 GB, no de 128 GB") ---

    @Test
    void unTituloQueSoloDiceLaRamDejaElAlmacenamientoSinConfirmar() {
        RequisitosBusqueda.Evaluacion e = RequisitosBusqueda.de("a16 128 gb").evaluar("Samsung A16 4 GB RAM", SIN_DECIR);
        assertEquals(Nivel.SIN_CONFIRMAR, e.nivel());
        assertEquals(List.of("El título no dice la capacidad (128 GB)"), e.motivos());
    }

    @Test
    void almacenamientoYRamEnElMismoTituloSeLeenSeparados() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("a16 128 gb");
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Samsung A16 128 GB 4 GB RAM", SIN_DECIR).nivel());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Samsung A16 4+128GB", SIN_DECIR).nivel());
        NormalizadorEspecificaciones.Capacidades c = NormalizadorEspecificaciones.capacidades("Samsung A16 128 GB 4 GB RAM");
        assertEquals(java.util.Set.of(4), c.ram());
        // Con la RAM dicha, la otra capacidad (mayor) es el almacenamiento: ya no queda suelta.
        assertEquals(java.util.Set.of(128), c.almacenamiento());
        assertEquals(java.util.Set.of(), c.sueltas());
    }

    @Test
    void unAlmacenamientoExplicitoDistintoNoCumple() {
        RequisitosBusqueda.Evaluacion e = RequisitosBusqueda.de("a16 128 gb")
                .evaluar("Samsung A16 64 GB de almacenamiento", SIN_DECIR);
        assertEquals(Nivel.INCUMPLE, e.nivel());
        assertEquals(List.of("Es de 64 GB, no de 128 GB"), e.motivos());
    }

    @Test
    void siPideAlmacenamientoYRamRevisaAmbosPorSeparado() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("a16 128gb 4gb ram");
        assertEquals(java.util.Set.of(128), requisitos.capacidadesGb());
        assertEquals(java.util.Set.of(4), requisitos.ramGb());
        assertEquals(List.of("modelo a16", "128 GB", "4 GB de RAM"), requisitos.etiquetas());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Samsung Galaxy A16 128 GB 4 GB RAM", SIN_DECIR).nivel());
        assertEquals(List.of("Tiene 6 GB de RAM, no 4 GB"),
                requisitos.evaluar("Samsung Galaxy A16 128 GB 6 GB RAM", SIN_DECIR).motivos());
        assertEquals(List.of("El título no dice la memoria RAM (4 GB)"),
                requisitos.evaluar("Samsung Galaxy A16 128 GB", SIN_DECIR).motivos());
        assertEquals(List.of("El título no dice la capacidad (128 GB)"),
                requisitos.evaluar("Samsung Galaxy A16 4 GB RAM", SIN_DECIR).motivos());
        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Samsung Galaxy A16 256 GB 4 GB RAM", SIN_DECIR).nivel());
    }

    @Test
    void laBusquedaYLaTablaComparativaLeenIgualElMismoTitulo() {
        for (String titulo : List.of("Samsung A16 4 GB RAM", "Samsung A16 128 GB 4 GB RAM",
                "Samsung A16 64 GB de almacenamiento", "Samsung A16 4+128GB")) {
            java.util.Map<String, String> tabla = NormalizadorEspecificaciones.extraer(titulo, List.of()).stream()
                    .collect(java.util.stream.Collectors.toMap(Especificacion::clave, Especificacion::valor));
            NormalizadorEspecificaciones.Capacidades c = NormalizadorEspecificaciones.capacidades(titulo);
            assertEquals(c.ram().isEmpty() ? null : c.ram().iterator().next() + " GB", tabla.get("ram"), titulo);
            boolean conAlmacenamiento = tabla.containsKey("almacenamiento");
            Nivel nivel = RequisitosBusqueda.de("a16 128 gb").evaluar(titulo, SIN_DECIR).nivel();
            // Si la tabla no muestra almacenamiento, la búsqueda tampoco puede decir que no cumple por capacidad.
            if (!conAlmacenamiento) {
                assertEquals(Nivel.SIN_CONFIRMAR, nivel, titulo);
            } else {
                assertEquals("128 GB".equals(tabla.get("almacenamiento")) ? Nivel.CUMPLE : Nivel.INCUMPLE, nivel, titulo);
            }
        }
    }

    @Test
    void otrasCategoriasConservanSuCapacidad() {
        assertEquals(Nivel.INCUMPLE, RequisitosBusqueda.de("memoria usb 16 gb").evaluar("Memoria USB Kingston 8 GB",
                SIN_DECIR).nivel());
        assertEquals(Nivel.CUMPLE, RequisitosBusqueda.de("memoria usb 16 gb").evaluar("Memoria USB Kingston 16GB",
                SIN_DECIR).nivel());
        assertEquals(Nivel.INCUMPLE, RequisitosBusqueda.de("disco 1 tb").evaluar("Disco externo 2 TB", SIN_DECIR).nivel());
    }

    // --- Capacidades según el tipo de producto (sin umbral general de 32 GB) ---

    @ParameterizedTest(name = "{0} con «{1}»: {2}")
    @CsvSource(delimiter = '|', value = {
        "Memoria USB 64 GB          | Memoria USB 16 GB              | INCUMPLE      | Es de 16 GB, no de 64 GB",
        "Memoria USB 16 GB          | Memoria USB 16 GB              | CUMPLE        | ''",
        "MicroSD 64 GB              | MicroSD 8 GB                   | INCUMPLE      | Es de 8 GB, no de 64 GB",
        "SSD 1 TB                   | SSD 2 TB                       | INCUMPLE      | Es de 2 TB, no de 1 TB",
        "A16 128 GB                 | A16 4 GB RAM                   | SIN_CONFIRMAR | El título no dice la capacidad (128 GB)",
        "A16 128 GB 4 GB RAM        | A16 128 GB 6 GB RAM            | INCUMPLE      | Tiene 6 GB de RAM, no 4 GB",
        "A16 128 GB 4 GB RAM        | A16 4+128 GB                   | CUMPLE        | ''",
        "tarjeta microsd 32 gb      | Tarjeta microSD SanDisk 32GB   | CUMPLE        | ''",
        "pendrive 64 gb             | Pendrive Kingston 128 GB       | INCUMPLE      | Es de 128 GB, no de 64 GB",
        "disco externo 1 tb         | Disco duro externo Seagate 1TB | CUMPLE        | ''",
    })
    void interpretaLasCapacidadesSegunElProducto(String busqueda, String titulo, Nivel esperado, String motivo) {
        RequisitosBusqueda.Evaluacion e = RequisitosBusqueda.de(busqueda).evaluar(titulo, SIN_DECIR);
        assertEquals(esperado, e.nivel(), e.motivos().toString());
        assertEquals(motivo.isEmpty() ? List.of() : List.of(motivo), e.motivos());
    }

    @Test
    void unaMemoriaUsbDe16GbMuestraSuAlmacenamientoEnLaTabla() {
        java.util.Map<String, String> tabla = NormalizadorEspecificaciones.extraer("Memoria USB 16 GB", List.of())
                .stream().collect(java.util.stream.Collectors.toMap(Especificacion::clave, Especificacion::valor));
        assertEquals("16 GB", tabla.get("almacenamiento"));
        assertEquals(null, tabla.get("ram"));
        assertEquals(CategoriaProducto.ALMACENAMIENTO, CategoriaProducto.detectar("Memoria USB 16 GB"));
    }

    @Test
    void laPalabraUsbSolaNoConvierteLaCapacidadEnAlmacenamiento() {
        // Puertos USB de un portátil: "16 GB" sola sigue siendo ambigua (podría ser la RAM).
        NormalizadorEspecificaciones.Capacidades portatil =
                NormalizadorEspecificaciones.capacidades("Portátil Lenovo 16 GB USB 3.0 HDMI");
        assertEquals(java.util.Set.of(), portatil.almacenamiento());
        assertEquals(java.util.Set.of(16), portatil.sueltas());
        assertEquals(Nivel.SIN_CONFIRMAR, RequisitosBusqueda.de("portatil 512 gb")
                .evaluar("Portátil Lenovo 16 GB USB 3.0 HDMI", SIN_DECIR).nivel());
        // Un celular con USB-C y ranura microSD sigue siendo un celular.
        assertEquals(CategoriaProducto.CELULAR, CategoriaProducto.detectar("Celular Motorola 128 GB USB-C ranura microSD"));
        assertEquals(CategoriaProducto.PORTATIL, CategoriaProducto.detectar("Portátil HP 8 GB RAM 512 GB SSD"));
        // Sin palabra de producto, "64GB USB 3.2" no se da por memoria USB.
        assertEquals(java.util.Set.of(64), NormalizadorEspecificaciones.capacidades("Kingston DataTraveler 64GB USB 3.2")
                .sueltas());
        // Una memoria nombrada primero sí lo es, aunque diga para qué equipo.
        assertEquals(CategoriaProducto.ALMACENAMIENTO, CategoriaProducto.detectar("Memoria microSD 64GB para celular"));
    }

    @Test
    void laRamExplicitaDeTresCifrasNoEsAlmacenamiento() {
        NormalizadorEspecificaciones.Capacidades c =
                NormalizadorEspecificaciones.capacidades("Estación de trabajo Dell 128 GB RAM 2 TB SSD");
        assertEquals(java.util.Set.of(128), c.ram());
        assertEquals(java.util.Set.of(2048), c.almacenamiento());
        assertEquals(java.util.Set.of(), c.sueltas());
        RequisitosBusqueda.Evaluacion e = RequisitosBusqueda.de("128 gb").evaluar("Portátil 128 GB RAM", SIN_DECIR);
        assertEquals(Nivel.SIN_CONFIRMAR, e.nivel(), "128 GB de RAM no es almacenamiento");
        RequisitosBusqueda ram = RequisitosBusqueda.de("portatil 128 gb ram");
        assertEquals(java.util.Set.of(128), ram.ramGb());
        assertEquals(java.util.Set.of(), ram.capacidadesGb());
        assertEquals(List.of("Tiene 64 GB de RAM, no 128 GB"),
                ram.evaluar("Portátil 64 GB RAM 1 TB SSD", SIN_DECIR).motivos());
    }

    @Test
    void enPortatilesYCelularesSeparaRamYAlmacenamientoPorElContexto() {
        NormalizadorEspecificaciones.Capacidades portatil =
                NormalizadorEspecificaciones.capacidades("Portátil HP 16GB 512GB SSD");
        assertEquals(java.util.Set.of(16), portatil.ram());
        assertEquals(java.util.Set.of(512), portatil.almacenamiento());
        NormalizadorEspecificaciones.Capacidades celular =
                NormalizadorEspecificaciones.capacidades("Celular Xiaomi Redmi 13C 8GB 256GB");
        assertEquals(java.util.Set.of(8), celular.ram());
        assertEquals(java.util.Set.of(256), celular.almacenamiento());
        // Una sola cifra que en ese equipo puede ser RAM queda ambigua: no cumple ni incumple.
        assertEquals(java.util.Set.of(8), NormalizadorEspecificaciones.capacidades("Celular Samsung A05 8GB").sueltas());
        assertEquals(Nivel.SIN_CONFIRMAR, RequisitosBusqueda.de("celular samsung a05 128gb")
                .evaluar("Celular Samsung A05 8GB", SIN_DECIR).nivel());
    }

    @Test
    void dosCapacidadesAmbiguasNoSeConviertenEnUnaInventada() {
        // Dos almacenamientos posibles en una memoria: la tabla no elige uno.
        java.util.Map<String, String> tabla = NormalizadorEspecificaciones.extraer("Kit memorias microSD 32GB 64GB",
                List.of()).stream().collect(java.util.stream.Collectors.toMap(Especificacion::clave, Especificacion::valor));
        assertEquals(null, tabla.get("almacenamiento"));
        // Tres cifras sueltas sin contexto: ninguna se asigna.
        NormalizadorEspecificaciones.Capacidades c = NormalizadorEspecificaciones.capacidades("Combo 4GB 64GB 128GB");
        assertEquals(java.util.Set.of(), c.ram());
        assertEquals(java.util.Set.of(), c.almacenamiento());
        assertEquals(java.util.Set.of(4, 64, 128), c.sueltas());
    }
}
