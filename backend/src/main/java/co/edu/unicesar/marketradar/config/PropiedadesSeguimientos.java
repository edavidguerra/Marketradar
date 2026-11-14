package co.edu.unicesar.marketradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cómo y cada cuánto se comprueban los precios de las ofertas seguidas. Los valores por defecto cuidan la cuota de
 * SerpApi (250 búsquedas al mes en el plan gratis): cada oferta se revisa cada 12 horas y cada ronda hace como máximo
 * 5 consultas a las tiendas.
 *
 * @param habilitado si la tarea del servidor revisa los precios
 * @param frecuenciaMinutos cada cuánto se comprueba cada oferta seguida
 * @param revisarCadaMinutos cada cuánto despierta la tarea para buscar seguimientos pendientes
 * @param maxSeguimientosPorRonda seguimientos que se revisan en una ronda
 * @param maxConsultasPorRonda consultas a las tiendas en una ronda (varias ofertas de la misma búsqueda gastan una)
 * @param bajaMinimaPorcentaje cuánto debe bajar el precio, como mínimo, para avisar
 * @param maxIntentosCorreo intentos de mandar un correo antes de darlo por fallido
 * @param horasVigenciaAviso un aviso que no se pudo mandar en este tiempo ya no se manda (el precio pudo cambiar)
 * @param maxPorUsuario seguimientos activos o pausados que puede tener una persona
 * @param tokenTareas clave para que un servicio externo (cron) despierte la tarea; vacío lo desactiva
 */
@ConfigurationProperties(prefix = "marketradar.seguimientos")
public record PropiedadesSeguimientos(boolean habilitado, int frecuenciaMinutos, int revisarCadaMinutos,
                                      int maxSeguimientosPorRonda, int maxConsultasPorRonda,
                                      BigDecimal bajaMinimaPorcentaje, int maxIntentosCorreo, int horasVigenciaAviso,
                                      int maxPorUsuario, String tokenTareas) {

    /** Pone valores seguros donde no se configuró nada. */
    public PropiedadesSeguimientos {
        frecuenciaMinutos = frecuenciaMinutos <= 0 ? 720 : Math.max(30, frecuenciaMinutos);
        revisarCadaMinutos = revisarCadaMinutos <= 0 ? 15 : revisarCadaMinutos;
        maxSeguimientosPorRonda = maxSeguimientosPorRonda <= 0 ? 25 : maxSeguimientosPorRonda;
        maxConsultasPorRonda = maxConsultasPorRonda <= 0 ? 5 : maxConsultasPorRonda;
        bajaMinimaPorcentaje = bajaMinimaPorcentaje == null || bajaMinimaPorcentaje.signum() < 0
                ? BigDecimal.ONE : bajaMinimaPorcentaje;
        maxIntentosCorreo = maxIntentosCorreo <= 0 ? 3 : maxIntentosCorreo;
        horasVigenciaAviso = horasVigenciaAviso <= 0 ? 48 : horasVigenciaAviso;
        maxPorUsuario = maxPorUsuario <= 0 ? 20 : maxPorUsuario;
    }

    @Override
    public String toString() {
        return "PropiedadesSeguimientos[habilitado=" + habilitado + ", frecuenciaMinutos=" + frecuenciaMinutos + "]";
    }
}
