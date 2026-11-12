package co.edu.unicesar.marketradar.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * @param capacidadesGb capacidades pedidas, en GB (1 TB = 1024 GB)
 * @param modelos números de modelo pedidos, por ejemplo {@code 13} o {@code s23}
 * @param versiones versiones pedidas junto al modelo, por ejemplo {@code pro} y {@code max}
 * @param pideCombo si la persona buscó un combo, kit o paquete
 */
public record RequisitosBusqueda(String texto, Optional<CondicionProducto> condicion, Set<Integer> capacidadesGb,
                                 List<Modelo> modelos, Set<String> versiones, boolean pideCombo) {

    /** Qué tan bien cumple una oferta lo que pidió la persona. */
    public enum Nivel { CUMPLE, SIN_CONFIRMAR, INCUMPLE }

    /**
     * Resultado de comparar una oferta.
     *
     * @param nivel grupo en que queda
     * @param motivos qué no cumple o qué no se pudo confirmar, en frases cortas para la persona
     */
    public record Evaluacion(Nivel nivel, List<String> motivos) {
        public Evaluacion {
            motivos = List.copyOf(motivos);
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

    private static final Pattern CAPACIDAD = Pattern.compile("(\\d{1,4})\\s?(gb|tb|gigas?|teras?)\\b");
    private static final Pattern MODELO = Pattern.compile("^([a-z]*)(\\d{1,5})([a-z]*)$");
    private static final Pattern COMBO = Pattern.compile("\\b(combo|kit|paquete|pack|bundle)\\b");
    private static final Set<String> PALABRAS_CONDICION = Set.of("nuevo", "nueva", "nuevos", "nuevas", "sellado",
            "sellada", "usado", "usada", "usados", "usadas", "reacondicionado", "reacondicionada", "refurbished",
            "renovado", "renovada", "exhibicion", "seminuevo", "seminueva", "segunda", "mano", "new", "used");
    private static final RequisitosBusqueda NINGUNO = new RequisitosBusqueda("", Optional.empty(), Set.of(),
            List.of(), Set.of(), false);

    public RequisitosBusqueda {
        capacidadesGb = Set.copyOf(capacidadesGb);
        modelos = List.copyOf(modelos);
        versiones = Set.copyOf(versiones);
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
        Set<Integer> capacidades = capacidades(limpio);
        String sinCapacidad = CAPACIDAD.matcher(limpio).replaceAll(" ");
        List<String> palabras = List.of(sinCapacidad.split(" "));
        List<Modelo> modelos = new ArrayList<>();
        Set<String> versiones = new LinkedHashSet<>();
        for (int i = 0; i < palabras.size(); i++) {
            Optional<Modelo> modelo = modelo(palabras.get(i));
            if (modelo.isPresent()) {
                String anterior = i > 0 && palabras.get(i - 1).matches("[a-z]{2,}") ? palabras.get(i - 1) : "";
                Modelo leido = modelo.get();
                modelos.add(new Modelo(leido.letras(), leido.numero(), leido.pegado(), anterior));
                versiones.addAll(versionesDesde(palabras, i, modelo.get()));
            }
        }
        return new RequisitosBusqueda(texto.strip(), condicion, capacidades, modelos, versiones,
                COMBO.matcher(limpio).find());
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
        Set<String> versiones = new LinkedHashSet<>(escritos.versiones);
        versiones.addAll(buscados.versiones);
        return new RequisitosBusqueda(escritos.texto, escritos.condicion.or(buscados::condicion), capacidades, modelos,
                versiones, escritos.pideCombo || buscados.pideCombo);
    }

    /** @return {@code true} si la persona no pidió nada en particular */
    public boolean estaVacio() {
        return condicion.isEmpty() && capacidadesGb.isEmpty() && modelos.isEmpty() && versiones.isEmpty();
    }

    /**
     * Lo que se exige, para mostrarlo: "modelo 13", "128 GB", "nuevo".
     *
     * @return frases cortas
     */
    public List<String> etiquetas() {
        List<String> etiquetas = new ArrayList<>();
        modelos.forEach(m -> etiquetas.add("modelo " + m));
        if (!versiones.isEmpty()) {
            etiquetas.add("versión " + String.join(" ", versiones));
        }
        capacidadesGb.stream().sorted().forEach(gb -> etiquetas.add(textoCapacidad(gb)));
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
        String limpio = CondicionProducto.normalizar(titulo == null ? "" : titulo);
        if (!pideCombo && COMBO.matcher(limpio).find()) {
            incumple.add("Es un combo o paquete con otros productos: su precio no se puede comparar");
        }
        revisarModelos(limpio, incumple, sinConfirmar);
        revisarCapacidad(limpio, incumple, sinConfirmar);
        condicion.ifPresent(pedida -> {
            if (condicionOferta.isEmpty()) {
                sinConfirmar.add("La tienda no dice si es " + pedida.texto().toLowerCase(java.util.Locale.ROOT));
            } else if (condicionOferta.get() != pedida) {
                incumple.add("Es " + condicionOferta.get().texto().toLowerCase(java.util.Locale.ROOT) + ", no "
                        + pedida.texto().toLowerCase(java.util.Locale.ROOT));
            }
        });
        if (!incumple.isEmpty()) {
            List<String> motivos = new ArrayList<>(incumple);
            motivos.addAll(sinConfirmar);
            return new Evaluacion(Nivel.INCUMPLE, motivos);
        }
        return sinConfirmar.isEmpty() ? new Evaluacion(Nivel.CUMPLE, List.of())
                : new Evaluacion(Nivel.SIN_CONFIRMAR, sinConfirmar);
    }

    private void revisarModelos(String titulo, List<String> incumple, List<String> sinConfirmar) {
        if (modelos.isEmpty()) {
            return;
        }
        List<String> palabras = List.of(CAPACIDAD.matcher(titulo).replaceAll(" ").split(" "));
        for (Modelo pedido : modelos) {
            int posicion = -1;
            boolean soloNumero = false;
            for (int i = 0; i < palabras.size() && posicion < 0; i++) {
                Optional<Modelo> delTitulo = modelo(palabras.get(i));
                if (delTitulo.isEmpty() || !delTitulo.get().numero().equals(pedido.numero())) {
                    continue;
                }
                String letras = delTitulo.get().letras();
                if (pedido.letras().isEmpty() || letras.endsWith(pedido.letras())) {
                    posicion = i;
                } else if (letras.isEmpty()) {
                    // "PlayStation 5" cuando se escribió "ps5": puede ser el mismo, pero no se puede asegurar.
                    soloNumero = true;
                }
            }
            if (posicion < 0) {
                if (soloNumero) {
                    sinConfirmar.add("No se pudo confirmar que sea el modelo " + pedido);
                } else if (tieneOtroModelo(palabras, pedido)) {
                    incumple.add("No es el modelo " + pedido);
                } else {
                    sinConfirmar.add("El título no dice el modelo " + pedido);
                }
                continue;
            }
            Set<String> delTitulo = new LinkedHashSet<>(versionesDesde(palabras, posicion,
                    modelo(palabras.get(posicion)).orElseThrow()));
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
    }

    private void revisarCapacidad(String titulo, List<String> incumple, List<String> sinConfirmar) {
        if (capacidadesGb.isEmpty()) {
            return;
        }
        Set<Integer> delTitulo = capacidades(titulo);
        for (Integer pedida : capacidadesGb) {
            if (delTitulo.contains(pedida)) {
                continue;
            }
            if (delTitulo.isEmpty()) {
                sinConfirmar.add("El título no dice la capacidad (" + textoCapacidad(pedida) + ")");
            } else {
                incumple.add("Es de " + String.join(" o ", delTitulo.stream().sorted()
                        .map(RequisitosBusqueda::textoCapacidad).toList()) + ", no de " + textoCapacidad(pedida));
            }
        }
    }

    /**
     * Otro modelo de la misma forma en el título: "14" cuando se pidió "13", "s24" cuando se pidió "s23", "8k" cuando
     * se pidió "4k". Un número de otra forma ("55" pulgadas, un año) no contradice nada.
     */
    private static boolean tieneOtroModelo(List<String> palabras, Modelo pedido) {
        return palabras.stream().map(RequisitosBusqueda::modelo).flatMap(Optional::stream)
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

    private static Set<Integer> capacidades(String texto) {
        Set<Integer> capacidades = new TreeSet<>();
        Matcher capacidad = CAPACIDAD.matcher(texto);
        while (capacidad.find()) {
            int valor = Integer.parseInt(capacidad.group(1));
            capacidades.add(capacidad.group(2).startsWith("t") ? valor * 1024 : valor);
        }
        return capacidades;
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
