package co.edu.unicesar.marketradar.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que la persona exigió al escribir la búsqueda, sacado del texto mismo y sin listas de productos: el modelo (los
 * números que escribió, como "13", "s23" o "6640"), la versión (Pro, Max, mini, Plus, Ultra...), la capacidad
 * ("128 GB") y la condición ("nuevo", "usado", "reacondicionado", "de exhibición").
 *
 * <p>Cada oferta se compara con estos requisitos y queda en uno de tres grupos ({@link Nivel}):</p>
 * <ul>
 *   <li>{@link Nivel#CUMPLE}: todo lo pedido aparece en la oferta.</li>
 *   <li>{@link Nivel#SIN_CONFIRMAR}: nada lo contradice, pero la tienda no dice algo (por ejemplo, si es nuevo).</li>
 *   <li>{@link Nivel#INCUMPLE}: algo lo contradice: otra versión, otra capacidad, otra condición o un combo.</li>
 * </ul>
 * Solo las que cumplen pueden ser la recomendación. Las demás se muestran aparte, con lo que les falta.
 *
 * @param texto lo que escribió la persona
 * @param condicion condición pedida, o vacío si no dijo
 * @param capacidadesGb almacenamiento (capacidad) pedido, en GB (1 TB = 1024 GB)
 * @param modelos números de modelo pedidos, por ejemplo {@code 13} o {@code s23}
 * @param versiones versiones pedidas junto al modelo, por ejemplo {@code pro} y {@code max}
 * @param pideCombo si la persona buscó un combo, kit o paquete
 * @param ramGb memoria RAM pedida, en GB ("4 GB RAM"); se revisa aparte del almacenamiento
 * @param identidad qué producto es: familia, tipo de accesorio, marca, variante, medidas y palabras clave
 *
 * <p><b>Identidad.</b> Antes de mirar capacidad o condición se revisa que la oferta sea el producto pedido: la familia
 * ({@link CategoriaProducto}), la marca si la escribió, el modelo junto a su serie ("playstation 5" exige la palabra
 * PlayStation seguida del 5; un 5 suelto, de "3.5 pulgadas" o "Bluetooth 5.0", no sirve) y la variante. Una oferta
 * solo CUMPLE con evidencia positiva; si algo la contradice, INCUMPLE; si falta información, queda SIN_CONFIRMAR. Lo
 * que es otro producto (otra familia, un accesorio, un juego, una caja o un modelo de otra serie como la Q21) queda
 * marcado como {@linkplain Evaluacion#ajena() ajeno}: no se muestra ni como alternativa.</p>
 */
public record RequisitosBusqueda(String texto, Optional<CondicionProducto> condicion, Set<Integer> capacidadesGb,
                                 List<Modelo> modelos, Set<String> versiones, boolean pideCombo, Set<Integer> ramGb,
                                 Identidad identidad) {

    /**
     * Qué tan bien cumple una oferta lo que pidió la persona. {@code OTRA_VARIANTE} es el mismo producto en una
     * variante que la persona no eligió ni rechazó (la edición Digital cuando buscó "playstation 5"): se muestra aparte
     * para no compararla como si fuera la misma, sin decir que incumple.
     */
    public enum Nivel { CUMPLE, SIN_CONFIRMAR, OTRA_VARIANTE, INCUMPLE }

    /**
     * Resultado de comparar una oferta.
     *
     * @param nivel grupo en que queda
     * @param motivos qué no cumple o qué no se pudo confirmar, en frases cortas para la persona
     * @param ajena si es otro producto (otra familia, un accesorio, otra serie): no sirve ni como alternativa
     */
    public record Evaluacion(Nivel nivel, List<String> motivos, boolean ajena) {
        public Evaluacion {
            motivos = List.copyOf(motivos);
        }

        public Evaluacion(Nivel nivel, List<String> motivos) {
            this(nivel, motivos, false);
        }
    }

    /**
     * Identidad del producto pedido.
     *
     * @param familia tipo de producto ({@link CategoriaProducto#DESCONOCIDA} si no se nombró)
     * @param accesorio tipo de accesorio pedido ("control", "funda"), o vacío si no buscó un accesorio
     * @param marcas marcas escritas ("Oster", "Sony"); las series ("iPhone") se revisan con el modelo
     * @param variantes variantes pedidas ("digital")
     * @param medidas medidas pedidas ("55 pulgadas", "600 W")
     * @param palabras palabras significativas que no son la familia, la marca, el modelo ni una característica
     *                 ("arrocera" en "olla arrocera oster"): reconocer la marca no las vuelve opcionales
     * @param atributos características pedidas de forma expresa: conectividad, funcionamiento, color
     */
    public record Identidad(CategoriaProducto familia, String accesorio, Set<String> marcas, Set<String> variantes,
                            List<TextoProducto.Medida> medidas, List<String> palabras,
                            List<AtributosProducto.Atributo> atributos) {
        public static final Identidad NINGUNA = new Identidad(CategoriaProducto.DESCONOCIDA, "", Set.of(), Set.of(),
                List.of(), List.of(), List.of());

        public Identidad {
            accesorio = accesorio == null ? "" : accesorio;
            marcas = Set.copyOf(marcas);
            variantes = Set.copyOf(variantes);
            medidas = List.copyOf(medidas);
            palabras = List.copyOf(palabras);
            atributos = atributos == null ? List.of() : List.copyOf(atributos);
        }

        /** Sin características pedidas. */
        public Identidad(CategoriaProducto familia, String accesorio, Set<String> marcas, Set<String> variantes,
                         List<TextoProducto.Medida> medidas, List<String> palabras) {
            this(familia, accesorio, marcas, variantes, medidas, palabras, List.of());
        }

        boolean estaVacia() {
            return familia == CategoriaProducto.DESCONOCIDA && marcas.isEmpty() && variantes.isEmpty()
                    && medidas.isEmpty() && palabras.isEmpty() && atributos.isEmpty();
        }
    }

    /**
     * Un número de modelo: letras antes, número y letras pegadas después ("s23", "13pro", "6640").
     *
     * @param letras letras antes del número ({@code s} en {@code s23}), puede ser vacío
     * @param numero el número
     * @param pegado letras pegadas al final ({@code pro} en {@code 13pro}), puede ser vacío
     * @param anterior palabra escrita antes de un número suelto ({@code playstation} en {@code playstation 5}), solo
     *                 para mostrarlo; puede ser vacío
     */
    public record Modelo(String letras, String numero, String pegado, String anterior) {
        public Modelo(String letras, String numero, String pegado) {
            this(letras, numero, pegado, "");
        }

        @Override
        public String toString() {
            String prefijo = letras.isEmpty() && !anterior.isEmpty() ? anterior + " " : "";
            return prefijo + letras + numero + pegado;
        }
    }

    /**
     * Palabras que, justo después del número del modelo, cambian la versión del producto. Son de uso general en
     * electrónica (celulares, tabletas, consolas, relojes), no de una marca.
     */
    static final Set<String> PALABRAS_VERSION = Set.of("pro", "max", "plus", "mini", "ultra", "lite", "fe", "neo",
            "prime");

    private static final Pattern MODELO = Pattern.compile("^([a-z]*)(\\d{1,5})([a-z]*)$");
    private static final Pattern COMBO = Pattern.compile("\\b(combo|kit|paquete|pack|bundle)\\b");
    /** "+ 2 juegos", "con 2 controles", "incluye juego": trae otros productos y su precio no es comparable. */
    private static final Pattern CON_OTROS = Pattern.compile("(\\+ ?(\\d+ )?|\\b(incluye|con|y|mas) (\\d+ )?)"
            + "(juegos?|controles|control adicional|audifonos|airpods|cargador|funda|forro|regalos?|obsequios?|"
            + "accesorios)\\b|\\+ ?\\d+ (juegos?|controles)\\b");
    /** Escrito justo antes de una serie, dice que no es esa serie: "tipo PlayStation", "estilo iPhone". */
    private static final Set<String> IMITACION = Set.of("tipo", "estilo", "similar", "como", "clon", "replica", "imitacion");
    /** Variantes de consola que cambian el producto: la edición Digital no tiene lector de discos. */
    private static final Map<String, String> VARIANTES_CONSOLA = Map.of("digital", "la edición Digital (sin lector de discos)");
    /** Palabras de una frase que no dicen qué producto es ("para mi nieto", "barato"). */
    private static final Set<String> RELLENO = Set.of("quiero", "quisiera", "necesito", "busco", "comprar", "pa", "mi",
            "nieto", "nieta", "hijo", "hija", "mama", "papa", "esposa", "esposo", "regalo", "regalar", "barato", "barata",
            "baratos", "baratas", "economico", "economica", "bueno", "buena", "buen", "mejor", "precio", "oferta",
            "original", "originales", "que", "sea", "me", "favor", "porfa", "envio", "gratis", "marca", "modelo",
            "referencia", "ram", "rom", "almacenamiento", "interno", "interna", "edicion", "edition", "version");
    private static final Set<String> PALABRAS_CONDICION = Set.of("nuevo", "nueva", "nuevos", "nuevas", "sellado",
            "sellada", "usado", "usada", "usados", "usadas", "reacondicionado", "reacondicionada", "refurbished",
            "renovado", "renovada", "exhibicion", "seminuevo", "seminueva", "segunda", "mano", "new", "used");
    private static final RequisitosBusqueda NINGUNO = new RequisitosBusqueda("", Optional.empty(), Set.of(),
            List.of(), Set.of(), false);

    public RequisitosBusqueda {
        capacidadesGb = Set.copyOf(capacidadesGb);
        ramGb = ramGb == null ? Set.of() : Set.copyOf(ramGb);
        modelos = List.copyOf(modelos);
        versiones = Set.copyOf(versiones);
        identidad = identidad == null ? Identidad.NINGUNA : identidad;
    }

    /** Requisitos sin identidad: solo modelo, versión, capacidad, RAM y condición. */
    public RequisitosBusqueda(String texto, Optional<CondicionProducto> condicion, Set<Integer> capacidadesGb,
                              List<Modelo> modelos, Set<String> versiones, boolean pideCombo, Set<Integer> ramGb) {
        this(texto, condicion, capacidadesGb, modelos, versiones, pideCombo, ramGb, Identidad.NINGUNA);
    }

    /** Requisitos sin RAM pedida ni identidad. */
    public RequisitosBusqueda(String texto, Optional<CondicionProducto> condicion, Set<Integer> capacidadesGb,
                              List<Modelo> modelos, Set<String> versiones, boolean pideCombo) {
        this(texto, condicion, capacidadesGb, modelos, versiones, pideCombo, Set.of());
    }

    /** @return requisitos vacíos: toda oferta cumple */
    public static RequisitosBusqueda ninguno() {
        return NINGUNO;
    }

    /**
     * Saca los requisitos de lo que escribió la persona.
     *
     * @param texto búsqueda
     * @return requisitos; vacíos si no escribió modelo, capacidad ni condición
     */
    public static RequisitosBusqueda de(String texto) {
        if (texto == null || texto.isBlank()) {
            return NINGUNO;
        }
        String limpio = CondicionProducto.normalizar(texto);
        Optional<CondicionProducto> condicion = CondicionProducto.detectar(limpio);
        // La misma interpretación que la tabla de comparación. Una capacidad que la persona escribió sin decir qué es
        // ("a16 128 gb") se pide como capacidad del producto: en la oferta tiene que aparecer como almacenamiento.
        NormalizadorEspecificaciones.Capacidades leidas = NormalizadorEspecificaciones.capacidades(texto);
        Set<Integer> ram = new TreeSet<>(leidas.ram());
        Set<Integer> capacidades = new TreeSet<>(leidas.almacenamiento());
        capacidades.addAll(leidas.sueltas());
        List<String> palabras = TextoProducto.palabras(texto);
        List<Modelo> modelos = new ArrayList<>();
        Set<String> versiones = new LinkedHashSet<>();
        for (int i = 0; i < palabras.size(); i++) {
            Optional<Modelo> modelo = modeloEn(palabras, i);
            if (modelo.isPresent()) {
                String anterior = i > 0 && palabras.get(i - 1).matches("[a-z]{2,}")
                        && !TextoProducto.VACIAS.contains(palabras.get(i - 1)) ? palabras.get(i - 1) : "";
                Modelo leido = modelo.get();
                modelos.add(new Modelo(leido.letras(), leido.numero(), leido.pegado(), anterior));
                versiones.addAll(versionesDesde(palabras, i, modelo.get()));
            }
        }
        boolean pideCombo = COMBO.matcher(limpio).find() || CON_OTROS.matcher(TextoProducto.leer(texto)).find();
        return new RequisitosBusqueda(texto.strip(), condicion, capacidades, modelos, versiones, pideCombo, ram,
                identidad(texto, palabras, modelos, versiones));
    }

    private static Identidad identidad(String texto, List<String> palabras, List<Modelo> modelos, Set<String> versiones) {
        String leido = String.join(" ", palabras);
        CategoriaProducto familia = CategoriaProducto.detectar(leido);
        String accesorio = familia == CategoriaProducto.ACCESORIO ? CategoriaProducto.tipoAccesorio(leido) : "";
        // Para un accesorio, la serie y la marca dicen con qué es compatible ("control para PS5"): se revisan con el
        // modelo, no como la marca del accesorio.
        Set<String> marcas = familia == CategoriaProducto.ACCESORIO ? Set.of() : TextoProducto.marcas(palabras, false);
        Set<String> variantes = new LinkedHashSet<>();
        if (familia == CategoriaProducto.CONSOLA) {
            VARIANTES_CONSOLA.keySet().stream().filter(palabras::contains).forEach(variantes::add);
        }
        List<TextoProducto.Medida> medidas = TextoProducto.medidas(palabras);
        // Las palabras que dicen qué es el producto cuentan siempre, aunque se haya reconocido una marca o un modelo:
        // "Oster" no dice que sea una olla arrocera. Las de una característica ("roja", "inalámbricos") se revisan
        // como característica, con sus sinónimos, y no como palabra literal.
        List<String> clave = new ArrayList<>();
        for (int i = 0; i < palabras.size(); i++) {
            String palabra = palabras.get(i);
            boolean deModelo = modelos.stream().anyMatch(m -> palabra.equals(m.anterior()));
            if (esPalabraClave(palabra) && !deModelo && !clave.contains(palabra)) {
                clave.add(palabra);
            }
        }
        // Un accesorio se reconoce por su tipo ("control", "mando"): sus demás palabras son la compatibilidad.
        if (familia == CategoriaProducto.ACCESORIO) {
            clave.clear();
        }
        return new Identidad(familia, accesorio, marcas, variantes, medidas, clave, AtributosProducto.lista(texto));
    }

    private static boolean esPalabraClave(String palabra) {
        return palabra.length() >= 3 && palabra.chars().noneMatch(Character::isDigit)
                && !TextoProducto.VACIAS.contains(palabra) && !RELLENO.contains(palabra)
                && !PALABRAS_CONDICION.contains(palabra) && CondicionProducto.detectar(palabra).isEmpty()
                && !PALABRAS_VERSION.contains(palabra) && !COMBO.matcher(palabra).find()
                && !palabra.matches("gb|tb|gigas?|teras?|pulgadas?|litros?|watts?|velocidades|unidades")
                && !TextoProducto.esSerieOMarca(palabra) && !AtributosProducto.esPalabraDeAtributo(palabra)
                && !VARIANTES_CONSOLA.containsKey(palabra) && !palabra.matches("color|colores|tipo|con|sin|telefonos?|computador(es)?|equipos?|aparatos?|dispositivos?|"
                + "productos?|articulos?")
                && CategoriaProducto.detectar(palabra) == CategoriaProducto.DESCONOCIDA;
    }

    /**
     * Revisa que la búsqueda corregida (por la IA o el diccionario) conserve lo que la persona pidió: el tipo de
     * producto (un control no se vuelve consola), la marca, los números de modelo y la versión.
     *
     * @param original lo que escribió la persona
     * @param corregida la búsqueda propuesta
     * @return {@code true} si la corrección se puede usar
     */
    public static boolean correccionConserva(String original, String corregida) {
        if (corregida == null || corregida.isBlank()) {
            return false;
        }
        RequisitosBusqueda escritos = de(original);
        RequisitosBusqueda buscados = de(corregida);
        CategoriaProducto antes = escritos.identidad.familia();
        CategoriaProducto despues = buscados.identidad.familia();
        if (antes != CategoriaProducto.DESCONOCIDA && antes != despues) {
            return false;
        }
        if (!buscados.identidad.marcas().containsAll(escritos.identidad.marcas())) {
            return false;
        }
        Set<String> numeros = new TreeSet<>();
        buscados.modelos.forEach(m -> numeros.add(m.letras() + m.numero()));
        boolean mismosModelos = escritos.modelos.stream().allMatch(m -> numeros.contains(m.letras() + m.numero()));
        // Tampoco puede quitar ni cambiar una característica pedida ("inalámbricos", "de aire", "roja").
        return mismosModelos && buscados.versiones.containsAll(escritos.versiones)
                && buscados.identidad.atributos().containsAll(escritos.identidad.atributos());
    }

    /**
     * Junta los requisitos de lo que escribió la persona con los de la búsqueda corregida. Los modelos salen de la
     * corregida (la IA escribe "playstation 5" en lugar de "ps5"), salvo que la corrección haya cambiado el número.
     * La capacidad, la condición y la versión que pidió la persona se conservan siempre, aunque la corrección las
     * haya quitado.
     *
     * @param original lo que escribió la persona
     * @param corregida lo que se buscó en la tienda
     * @return requisitos combinados
     */
    public static RequisitosBusqueda combinar(String original, String corregida) {
        RequisitosBusqueda escritos = de(original);
        if (corregida == null || corregida.isBlank() || corregida.equalsIgnoreCase(original)) {
            return escritos;
        }
        RequisitosBusqueda buscados = de(corregida);
        Set<String> numerosBuscados = new TreeSet<>();
        buscados.modelos.forEach(m -> numerosBuscados.add(m.numero()));
        List<Modelo> modelos = new ArrayList<>(buscados.modelos);
        escritos.modelos.stream().filter(m -> !numerosBuscados.contains(m.numero())).forEach(modelos::add);
        Set<Integer> capacidades = new TreeSet<>(escritos.capacidadesGb);
        capacidades.addAll(buscados.capacidadesGb);
        Set<Integer> ram = new TreeSet<>(escritos.ramGb);
        ram.addAll(buscados.ramGb);
        Set<String> versiones = new LinkedHashSet<>(escritos.versiones);
        versiones.addAll(buscados.versiones);
        // La identidad de lo que escribió la persona manda: si la corrección dejó de decir "control", se sigue
        // buscando un control. Las marcas, variantes y medidas de las dos se exigen juntas.
        Identidad dicha = escritos.identidad;
        Identidad buscada = buscados.identidad;
        CategoriaProducto familia = dicha.familia() != CategoriaProducto.DESCONOCIDA ? dicha.familia() : buscada.familia();
        Set<String> marcas = new LinkedHashSet<>(dicha.marcas());
        marcas.addAll(buscada.marcas());
        Set<String> variantes = new LinkedHashSet<>(dicha.variantes());
        variantes.addAll(buscada.variantes());
        List<TextoProducto.Medida> medidas = new ArrayList<>(dicha.medidas());
        buscada.medidas().stream().filter(m -> medidas.stream().noneMatch(m::mismaQue)).forEach(medidas::add);
        // Las palabras clave salen de la búsqueda corregida (la original puede traer saludos, relleno o errores de
        // escritura). Las características se juntan: se leen con sinónimos, así que un error no las cambia.
        List<String> palabras = familia == CategoriaProducto.ACCESORIO ? List.of() : buscada.palabras();
        List<AtributosProducto.Atributo> atributos = new ArrayList<>(dicha.atributos());
        buscada.atributos().stream().filter(a -> !atributos.contains(a)).forEach(atributos::add);
        Identidad identidad = new Identidad(familia, dicha.accesorio().isEmpty() ? buscada.accesorio() : dicha.accesorio(),
                marcas, variantes, medidas, palabras, atributos);
        return new RequisitosBusqueda(escritos.texto, escritos.condicion.or(buscados::condicion), capacidades, modelos,
                versiones, escritos.pideCombo || buscados.pideCombo, ram, identidad);
    }

    /** @return {@code true} si la persona no pidió nada en particular */
    public boolean estaVacio() {
        return condicion.isEmpty() && capacidadesGb.isEmpty() && ramGb.isEmpty() && modelos.isEmpty()
                && versiones.isEmpty() && identidad.marcas().isEmpty() && identidad.medidas().isEmpty()
                && identidad.variantes().isEmpty() && identidad.atributos().isEmpty();
    }

    /**
     * Lo que se exige, para mostrarlo: "modelo 13", "128 GB", "nuevo".
     *
     * @return frases cortas
     */
    public List<String> etiquetas() {
        List<String> etiquetas = new ArrayList<>();
        identidad.marcas().forEach(m -> etiquetas.add("marca " + m));
        modelos.forEach(m -> etiquetas.add("modelo " + m));
        if (!versiones.isEmpty()) {
            etiquetas.add("versión " + String.join(" ", versiones));
        }
        identidad.variantes().forEach(v -> etiquetas.add("edición " + v));
        identidad.medidas().forEach(m -> etiquetas.add(m.toString()));
        identidad.atributos().forEach(a -> etiquetas.add("color".equals(a.grupo()) ? "color " + a.valor() : a.valor()));
        capacidadesGb.stream().sorted().forEach(gb -> etiquetas.add(textoCapacidad(gb)));
        ramGb.stream().sorted().forEach(gb -> etiquetas.add(textoCapacidad(gb) + " de RAM"));
        condicion.ifPresent(c -> etiquetas.add(c.texto().toLowerCase(java.util.Locale.ROOT)));
        return etiquetas;
    }

    /**
     * Compara una oferta con los requisitos.
     *
     * @param titulo título de la oferta
     * @param condicionOferta condición que informó la tienda, o vacío si no la informó
     * @return grupo y motivos
     */
    public Evaluacion evaluar(String titulo, Optional<CondicionProducto> condicionOferta) {
        List<String> incumple = new ArrayList<>();
        List<String> sinConfirmar = new ArrayList<>();
        String original = titulo == null ? "" : titulo;
        String limpio = CondicionProducto.normalizar(original);
        List<String> palabras = TextoProducto.palabras(original);
        Revision revision = new Revision();
        revisarFamilia(palabras, revision, incumple, sinConfirmar);
        if (!pideCombo && (COMBO.matcher(limpio).find() || CON_OTROS.matcher(String.join(" ", palabras)).find())) {
            incumple.add("Es un combo o paquete con otros productos: su precio no se puede comparar");
        }
        revisarMarca(palabras, revision, incumple, sinConfirmar);
        revisarModelos(palabras, revision, incumple, sinConfirmar);
        List<String> otraVariante = new ArrayList<>();
        revisarVariantes(palabras, otraVariante, sinConfirmar);
        revisarMedidas(palabras, incumple, sinConfirmar);
        revisarAtributos(original, incumple, sinConfirmar);
        revisarPalabras(palabras, revision, sinConfirmar);
        revisarCapacidad(original, incumple, sinConfirmar);
        condicion.ifPresent(pedida -> {
            if (condicionOferta.isEmpty()) {
                sinConfirmar.add("La tienda no dice si es " + pedida.texto().toLowerCase(java.util.Locale.ROOT));
            } else if (condicionOferta.get() != pedida) {
                incumple.add("Es " + condicionOferta.get().texto().toLowerCase(java.util.Locale.ROOT) + ", no "
                        + pedida.texto().toLowerCase(java.util.Locale.ROOT));
            }
        });
        // Sin evidencia positiva no se confirma: si se pidió un tipo de producto y el título no lo dice, y tampoco
        // nombra el modelo, la oferta queda por confirmar aunque nada la contradiga.
        if (incumple.isEmpty() && revision.familiaSinConfirmar && !revision.modeloConfirmado) {
            sinConfirmar.add(0, "El título no dice que sea " + articulo(identidad.familia()));
        }
        if (!incumple.isEmpty()) {
            List<String> motivos = new ArrayList<>(incumple);
            motivos.addAll(sinConfirmar);
            return new Evaluacion(Nivel.INCUMPLE, motivos, revision.ajena);
        }
        if (!otraVariante.isEmpty()) {
            List<String> motivos = new ArrayList<>(otraVariante);
            motivos.addAll(sinConfirmar);
            return new Evaluacion(Nivel.OTRA_VARIANTE, motivos);
        }
        return sinConfirmar.isEmpty() ? new Evaluacion(Nivel.CUMPLE, List.of())
                : new Evaluacion(Nivel.SIN_CONFIRMAR, sinConfirmar);
    }

    /**
     * Las características pedidas de forma expresa: si el título dice otra del mismo grupo, no cumple ("Es con cable,
     * no inalámbrico"); si no dice ninguna, queda por confirmar.
     */
    private void revisarAtributos(String titulo, List<String> incumple, List<String> sinConfirmar) {
        if (identidad.atributos().isEmpty()) {
            return;
        }
        Map<String, Set<String>> delTitulo = AtributosProducto.leer(titulo);
        for (AtributosProducto.Atributo pedido : identidad.atributos()) {
            Set<String> tiene = delTitulo.getOrDefault(pedido.grupo(), Set.of());
            if (tiene.contains(pedido.valor())) {
                continue;
            }
            if (tiene.isEmpty()) {
                sinConfirmar.add(AtributosProducto.falta(pedido));
            } else {
                incumple.add(AtributosProducto.contradice(pedido, tiene));
            }
        }
    }

    /** Lo que se fue sabiendo de la oferta mientras se revisaba. */
    private static final class Revision {
        boolean ajena;
        boolean familiaSinConfirmar;
        boolean modeloConfirmado;
        boolean marcaConfirmada;
    }

    private void revisarFamilia(List<String> palabras, Revision revision, List<String> incumple,
                                List<String> sinConfirmar) {
        CategoriaProducto pedida = identidad.familia();
        String leido = String.join(" ", palabras);
        CategoriaProducto deLaOferta = CategoriaProducto.detectar(leido);
        if (pedida == CategoriaProducto.DESCONOCIDA) {
            // Sin una familia reconocida, lo que dice qué es son sus palabras ("arrocera"). Si el título es claramente
            // otro tipo de producto (una licuadora) y no nombra ninguna de ellas, es otro producto aunque la marca
            // coincida.
            if (!identidad.palabras().isEmpty() && deLaOferta != CategoriaProducto.DESCONOCIDA
                    && identidad.palabras().stream().noneMatch(p -> TextoProducto.aparece(p, palabras))) {
                incumple.add("Es " + articulo(deLaOferta) + ", no lo que buscó («" + String.join(" ", identidad.palabras())
                        + "»)");
                revision.ajena = true;
            }
            return;
        }
        if (pedida == CategoriaProducto.ACCESORIO) {
            String tipo = CategoriaProducto.tipoAccesorio(leido);
            if (deLaOferta != CategoriaProducto.ACCESORIO) {
                incumple.add("No es " + (identidad.accesorio().isEmpty() ? "un accesorio" : "un " + identidad.accesorio())
                        + ": es " + articulo(deLaOferta));
                revision.ajena = true;
            } else if (!identidad.accesorio().isEmpty() && !tipo.isEmpty() && !tipo.equals(identidad.accesorio())) {
                incumple.add("Es otro accesorio (" + tipo + "), no un " + identidad.accesorio());
                revision.ajena = true;
            } else if (!identidad.accesorio().isEmpty() && tipo.isEmpty()) {
                sinConfirmar.add("El título no dice que sea un " + identidad.accesorio());
            }
            return;
        }
        if (deLaOferta == pedida) {
            return;
        }
        if (deLaOferta.esComplemento()) {
            incumple.add("Es " + articulo(deLaOferta) + " para el producto, no " + articulo(pedida));
            revision.ajena = true;
        } else if (deLaOferta != CategoriaProducto.DESCONOCIDA) {
            incumple.add("Es " + articulo(deLaOferta) + ", no " + articulo(pedida));
            revision.ajena = true;
        } else {
            revision.familiaSinConfirmar = true;
        }
    }

    private void revisarMarca(List<String> palabras, Revision revision, List<String> incumple,
                              List<String> sinConfirmar) {
        if (identidad.marcas().isEmpty()) {
            return;
        }
        Set<String> delTitulo = TextoProducto.marcas(palabras, true);
        for (String marca : identidad.marcas()) {
            if (delTitulo.contains(marca)) {
                revision.marcaConfirmada = true;
            } else if (!delTitulo.isEmpty()) {
                incumple.add("Es de la marca " + String.join(" o ", delTitulo) + ", no " + marca);
            } else {
                sinConfirmar.add("El título no dice la marca " + marca);
            }
        }
    }

    private void revisarModelos(List<String> palabras, Revision revision, List<String> incumple,
                                List<String> sinConfirmar) {
        if (modelos.isEmpty()) {
            return;
        }
        boolean todos = true;
        for (Modelo pedido : modelos) {
            int posicion = buscarModelo(palabras, pedido);
            if (posicion < 0) {
                todos = false;
                if (serieDe(pedido)) {
                    revisarSerieAusente(palabras, pedido, revision, incumple, sinConfirmar);
                } else if (tieneOtroModelo(palabras, pedido)) {
                    incumple.add("No es el modelo " + pedido);
                } else {
                    sinConfirmar.add("El título no dice el modelo " + pedido);
                }
                continue;
            }
            Set<String> delTitulo = new LinkedHashSet<>(versionesDesde(palabras, posicion,
                    modeloEn(palabras, posicion).orElseThrow()));
            Set<String> pedidas = new LinkedHashSet<>(versiones);
            if (!delTitulo.equals(pedidas)) {
                Set<String> sobran = new LinkedHashSet<>(delTitulo);
                sobran.removeAll(pedidas);
                Set<String> faltan = new LinkedHashSet<>(pedidas);
                faltan.removeAll(delTitulo);
                if (!sobran.isEmpty()) {
                    incumple.add("Es la versión " + titulo(delTitulo) + ", no la que pidió");
                } else {
                    incumple.add("No es la versión " + titulo(faltan));
                }
            }
        }
        revision.modeloConfirmado = todos;
    }

    /**
     * Posición del modelo pedido en el título, o -1. Un número corto pedido junto a su serie ("playstation 5",
     * "iphone 13") tiene que aparecer junto a esa serie: un 5 o un 13 sueltos pueden ser cualquier cosa.
     */
    private static int buscarModelo(List<String> palabras, Modelo pedido) {
        boolean exigeSerie = serieDe(pedido);
        for (int i = 0; i < palabras.size(); i++) {
            Optional<Modelo> delTitulo = modeloEn(palabras, i);
            if (delTitulo.isEmpty() || !delTitulo.get().numero().equals(pedido.numero())) {
                continue;
            }
            String letras = delTitulo.get().letras();
            if (!pedido.letras().isEmpty()) {
                if (letras.endsWith(pedido.letras())) {
                    return i;
                }
                continue;
            }
            if (!letras.isEmpty()) {
                continue;
            }
            if (!exigeSerie || (i > 0 && palabras.get(i - 1).equals(pedido.anterior()) && !imitacion(palabras, i - 1))) {
                return i;
            }
        }
        return -1;
    }

    /** El modelo se pidió como serie y número corto ("playstation 5", "iphone 13", "xiaomi 14"). */
    private static boolean serieDe(Modelo pedido) {
        return pedido.letras().isEmpty() && !pedido.anterior().isEmpty() && pedido.numero().length() <= 2
                && TextoProducto.esSerieOMarca(pedido.anterior());
    }

    private static boolean imitacion(List<String> palabras, int posicionSerie) {
        return posicionSerie > 0 && IMITACION.contains(palabras.get(posicionSerie - 1));
    }

    /**
     * El título no trae la serie seguida del número pedido. Si nombra la serie con otro número, es otro modelo de la
     * misma serie (se muestra como alternativa). Si no nombra la serie pero sí otra serie u otro modelo ("Q21",
     * "X5"), es otro producto. Si no dice nada, falta confirmar.
     */
    private void revisarSerieAusente(List<String> palabras, Modelo pedido, Revision revision, List<String> incumple,
                                     List<String> sinConfirmar) {
        String nombre = bonito(pedido);
        for (int i = 0; i < palabras.size(); i++) {
            if (!palabras.get(i).equals(pedido.anterior()) || imitacion(palabras, i)) {
                continue;
            }
            Optional<Modelo> siguiente = i + 1 < palabras.size() ? modeloEn(palabras, i + 1) : Optional.empty();
            if (siguiente.isPresent()) {
                incumple.add("No es el modelo " + pedido + ": es " + pedido.anterior() + " " + siguiente.get().letras()
                        + siguiente.get().numero() + siguiente.get().pegado());
            } else {
                sinConfirmar.add("El título no dice el modelo " + pedido);
            }
            return;
        }
        Optional<String> otro = otroProducto(palabras, pedido);
        if (otro.isPresent()) {
            incumple.add("No es " + nombre + ": el título nombra otro producto (" + otro.get() + ")");
            revision.ajena = true;
        } else {
            sinConfirmar.add("El título no dice que sea " + nombre);
        }
    }

    /** Otra serie ("nintendo switch") o un código de modelo con letras y números ("q21", "x5") que no es lo pedido. */
    private static Optional<String> otroProducto(List<String> palabras, Modelo pedido) {
        for (int i = 0; i < palabras.size(); i++) {
            String palabra = palabras.get(i);
            if (!palabra.equals(pedido.anterior()) && NormalizadorEspecificaciones.esSerie(palabra)
                    && !IMITACION.contains(i > 0 ? palabras.get(i - 1) : "")) {
                return Optional.of(palabra.toUpperCase(java.util.Locale.ROOT).charAt(0) + palabra.substring(1));
            }
            Optional<Modelo> modelo = modeloEn(palabras, i);
            if (modelo.isPresent() && !modelo.get().letras().isEmpty() && modelo.get().letras().length() <= 3) {
                return Optional.of(palabra.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    private void revisarVariantes(List<String> palabras, List<String> incumple, List<String> sinConfirmar) {
        // Si pidió una edición y el título trae otra, es una contradicción; si no pidió ninguna, es otra variante.
        if (identidad.familia() != CategoriaProducto.CONSOLA) {
            return;
        }
        for (Map.Entry<String, String> variante : VARIANTES_CONSOLA.entrySet()) {
            boolean pedida = identidad.variantes().contains(variante.getKey());
            boolean tiene = palabras.contains(variante.getKey());
            if (tiene && !pedida) {
                // La persona no eligió edición: no se le atribuye que la rechazó, pero tampoco se compara como si
                // fuera la misma.
                incumple.add("Es " + variante.getValue() + ". Usted no eligió edición: se muestra aparte para no "
                        + "compararla con la estándar");
            } else if (pedida && !tiene) {
                sinConfirmar.add("El título no dice que sea " + variante.getValue());
            }
        }
    }

    private void revisarMedidas(List<String> palabras, List<String> incumple, List<String> sinConfirmar) {
        if (identidad.medidas().isEmpty()) {
            return;
        }
        List<TextoProducto.Medida> delTitulo = TextoProducto.medidas(palabras);
        for (TextoProducto.Medida pedida : identidad.medidas()) {
            List<TextoProducto.Medida> mismaUnidad = delTitulo.stream()
                    .filter(m -> m.unidad().equals(pedida.unidad())).toList();
            if (mismaUnidad.stream().anyMatch(pedida::mismaQue)) {
                continue;
            }
            if (mismaUnidad.isEmpty()) {
                sinConfirmar.add("El título no dice la medida (" + pedida + ")");
            } else {
                incumple.add("Es de " + String.join(" o ", mismaUnidad.stream().map(Object::toString).toList())
                        + ", no de " + pedida);
            }
        }
    }

    private void revisarPalabras(List<String> palabras, Revision revision, List<String> sinConfirmar) {
        for (String palabra : identidad.palabras()) {
            if (!TextoProducto.aparece(palabra, palabras)) {
                sinConfirmar.add("El título no dice «" + palabra + "»");
            }
        }
    }

    private static String articulo(CategoriaProducto familia) {
        return switch (familia) {
            case ACCESORIO -> "un accesorio";
            case VIDEOJUEGO -> "un videojuego o contenido digital";
            case EMPAQUE -> "una caja o empaque sin el producto";
            case DESCONOCIDA -> "otro producto";
            case AUDIFONOS -> "unos audífonos";
            case CELULAR, TELEVISOR, TENSIOMETRO, RELOJ, PORTATIL ->
                    "un " + familia.texto().toLowerCase(java.util.Locale.ROOT);
            default -> "una " + familia.texto().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static String bonito(Modelo modelo) {
        String serie = modelo.anterior();
        String nombre = serie.startsWith("i") && serie.length() > 1 ? "i" + Character.toUpperCase(serie.charAt(1))
                + serie.substring(2) : Character.toUpperCase(serie.charAt(0)) + serie.substring(1);
        if ("playstation".equals(serie)) {
            nombre = "PlayStation";
        }
        return nombre + " " + modelo.numero() + modelo.pegado();
    }

    /**
     * Almacenamiento y RAM por separado, con la misma interpretación que la tabla de comparación
     * ({@link NormalizadorEspecificaciones#capacidades(String)}). Una capacidad unida a la palabra RAM nunca se toma
     * como almacenamiento, y una que el título deja ambigua (por ejemplo "16 GB" sola en un portátil) no confirma ni
     * contradice: queda "sin confirmar". Solo se dice que no cumple si el título trae una capacidad identificada que
     * contradice lo pedido.
     */
    private void revisarCapacidad(String titulo, List<String> incumple, List<String> sinConfirmar) {
        if (capacidadesGb.isEmpty() && ramGb.isEmpty()) {
            return;
        }
        NormalizadorEspecificaciones.Capacidades delTitulo = NormalizadorEspecificaciones.capacidades(titulo);
        for (Integer pedida : capacidadesGb) {
            Set<Integer> almacenamiento = new TreeSet<>(delTitulo.almacenamiento());
            if (almacenamiento.contains(pedida)) {
                continue;
            }
            if (almacenamiento.isEmpty()) {
                sinConfirmar.add("El título no dice la capacidad (" + textoCapacidad(pedida) + ")");
            } else {
                incumple.add("Es de " + String.join(" o ", almacenamiento.stream()
                        .map(RequisitosBusqueda::textoCapacidad).toList()) + ", no de " + textoCapacidad(pedida));
            }
        }
        for (Integer pedida : ramGb) {
            if (delTitulo.ram().contains(pedida)) {
                continue;
            }
            if (delTitulo.ram().isEmpty()) {
                sinConfirmar.add("El título no dice la memoria RAM (" + textoCapacidad(pedida) + ")");
            } else {
                incumple.add("Tiene " + String.join(" o ", new TreeSet<>(delTitulo.ram()).stream()
                        .map(RequisitosBusqueda::textoCapacidad).toList()) + " de RAM, no " + textoCapacidad(pedida));
            }
        }
    }

    /**
     * Otro modelo de la misma forma en el título: "14" cuando se pidió "13", "s24" cuando se pidió "s23", "8k" cuando
     * se pidió "4k". Un número de otra forma ("55" pulgadas, un año) no contradice nada.
     */
    private static boolean tieneOtroModelo(List<String> palabras, Modelo pedido) {
        List<Modelo> delTitulo = new ArrayList<>();
        for (int i = 0; i < palabras.size(); i++) {
            modeloEn(palabras, i).ifPresent(delTitulo::add);
        }
        return delTitulo.stream()
                .filter(m -> !(m.numero().length() == 4 && m.numero().startsWith("20")))
                .anyMatch(m -> !m.numero().equals(pedido.numero()) && m.letras().endsWith(pedido.letras())
                        && (m.pegado().equals(pedido.pegado()) || PALABRAS_VERSION.contains(m.pegado()))
                        && (pedido.letras().isEmpty() == m.letras().isEmpty()));
    }

    private static List<String> versionesDesde(List<String> palabras, int posicion, Modelo modelo) {
        List<String> versiones = new ArrayList<>();
        if (PALABRAS_VERSION.contains(modelo.pegado())) {
            versiones.add(modelo.pegado());
        }
        for (int i = posicion + 1; i < palabras.size() && PALABRAS_VERSION.contains(palabras.get(i)); i++) {
            versiones.add(palabras.get(i));
        }
        return versiones;
    }

    /** El modelo escrito en esa posición, si es un modelo y no una medida, cantidad o versión de conectividad. */
    private static Optional<Modelo> modeloEn(List<String> palabras, int i) {
        if (TextoProducto.esEspecificacion(palabras, i)) {
            return Optional.empty();
        }
        return modelo(palabras.get(i));
    }

    private static Optional<Modelo> modelo(String palabra) {
        if (palabra.isEmpty() || PALABRAS_CONDICION.contains(palabra)) {
            return Optional.empty();
        }
        Matcher partes = MODELO.matcher(palabra);
        if (!partes.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Modelo(partes.group(1), partes.group(2).replaceFirst("^0+(?=\\d)", ""),
                partes.group(3)));
    }

    private static String textoCapacidad(int gb) {
        return gb >= 1024 && gb % 1024 == 0 ? (gb / 1024) + " TB" : gb + " GB";
    }

    private static String titulo(Set<String> palabras) {
        List<String> partes = new ArrayList<>();
        for (String palabra : palabras) {
            partes.add(palabra.length() <= 2 ? palabra.toUpperCase(java.util.Locale.ROOT)
                    : Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1));
        }
        return String.join(" ", partes);
    }
}
