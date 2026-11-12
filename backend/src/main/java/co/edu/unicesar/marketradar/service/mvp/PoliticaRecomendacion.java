package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;

import java.util.List;

/**
 * Cómo se elige la recomendación, en palabras sencillas y con los pesos que de verdad se usan. Es la única
 * explicación: la muestran la página ("¿Cómo se calcula?") y el asistente, para que nunca digan cosas distintas.
 * Si cambia {@link SeleccionPonderadaMvp}, se cambia aquí.
 */
public final class PoliticaRecomendacion {

    private PoliticaRecomendacion() {
    }

    /**
     * @param pesos pesos configurados
     * @return frases que explican la recomendación y el puntaje
     */
    public static List<String> explicar(PropiedadesMarketradar.Mvp pesos) {
        return List.of(
                "Solo recomendamos una oferta que cumple todo lo que usted pidió (modelo, versión, capacidad y "
                        + "condición) y cuyo precio no tiene alertas. Si ninguna cumple, no recomendamos ninguna y le "
                        + "mostramos las alternativas con lo que les falta.",
                "El puntaje va de 0 a 100 y compara solo las ofertas que cumplen. Si filtra por precio, se vuelve a "
                        + "calcular con las que quedan. No es una probabilidad ni una garantía de calidad.",
                "Precio (" + porcentaje(pesos.pesoPrecio()) + "): la más barata recibe todo el puntaje y las demás, en "
                        + "proporción. A una oferta que no informa el envío, para comparar, se le suma el envío más "
                        + "caro de las demás; así no gana solo porque no se sabe cuánto cobra.",
                "Opiniones (" + porcentaje(pesos.pesoCalificacion()) + "): promedio de estrellas ajustado por la "
                        + "cantidad. Con menos de " + pesos.resenasMinimas() + " opiniones se acerca a 3 de 5, y sin "
                        + "opiniones cuenta como 3 de 5. En Google Shopping son opiniones del producto reunidas de "
                        + "varias tiendas, no opiniones sobre el vendedor.",
                "Envío (" + porcentaje(pesos.pesoEnvio()) + "): gratis suma todo; con costo, suma menos mientras más "
                        + "pese en el total; si no se conoce, es aproximado o la tienda vende desde el exterior, no "
                        + "suma.",
                "Si la tienda no confirma que el producto es nuevo (es usado, reacondicionado, de exhibición o no lo "
                        + "dice), el puntaje baja un " + rebaja(SeleccionPonderadaMvp.PENALIZACION_NO_NUEVO)
                        + ". Si vende desde el exterior, baja otro " + rebaja(SeleccionPonderadaMvp.PENALIZACION_EXTERIOR)
                        + ", porque puede cobrar impuestos de importación que no están en el precio.",
                "La confianza (alta, media o baja) dice cuántos datos faltaron: el envío, los impuestos de "
                        + "importación, las opiniones, la condición o desde dónde vende la tienda.");
    }

    private static String rebaja(double factor) {
        return porcentaje(1 - factor);
    }

    private static String porcentaje(double peso) {
        return Math.round(peso * 100) + " %";
    }
}
