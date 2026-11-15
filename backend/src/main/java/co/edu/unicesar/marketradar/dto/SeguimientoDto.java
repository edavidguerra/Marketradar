package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioSeguimientos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Un seguimiento como lo ve la persona en "Seguimientos".
 *
 * @param ofertaId oferta seguida
 * @param titulo producto
 * @param tienda código del marketplace
 * @param vendedor tienda para mostrar
 * @param urlImagen foto
 * @param urlDestino enlace de la oferta
 * @param enlace a dónde lleva el enlace
 * @param precioGuardado último precio guardado de la oferta (de la búsqueda o de la última comprobación)
 * @param moneda moneda de los precios
 * @param tipoAviso {@code baja_precio} o {@code precio_objetivo}
 * @param precioObjetivo precio objetivo, o {@code null}
 * @param estado {@code activo} o {@code pausado}
 * @param ultimaComprobacion cuándo se comprobó el precio por última vez, o {@code null}
 * @param proximaComprobacion cuándo se comprobará otra vez
 * @param resultado código de lo que pasó en la última comprobación
 * @param resultadoTexto lo mismo, en palabras sencillas
 * @param creadoEn desde cuándo lo sigue
 * @param avisos últimos correos
 */
public record SeguimientoDto(long ofertaId, String titulo, String tienda, String vendedor, String urlImagen,
                             String urlDestino, OfertaDto.EnlaceDto enlace, BigDecimal precioGuardado, String moneda,
                             String tipoAviso, BigDecimal precioObjetivo, String estado,
                             OffsetDateTime ultimaComprobacion, OffsetDateTime proximaComprobacion, String resultado,
                             String resultadoTexto, OffsetDateTime creadoEn, List<AvisoDto> avisos) {

    /**
     * Un correo del seguimiento.
     *
     * @param tipo {@code confirmacion}, {@code baja_precio} o {@code precio_objetivo}
     * @param estado {@code pendiente}, {@code enviado}, {@code fallido} o {@code descartado}
     * @param estadoTexto el estado en palabras
     * @param precioAnterior precio antes, si aplica
     * @param precioActual precio del aviso
     * @param creadoEn cuándo se generó
     * @param enviadoEn cuándo lo aceptó el servicio de correo (no es la hora de entrega), o {@code null}
     */
    public record AvisoDto(String tipo, String estado, String estadoTexto, BigDecimal precioAnterior,
                           BigDecimal precioActual, OffsetDateTime creadoEn, OffsetDateTime enviadoEn) {
    }

    /**
     * @param vista seguimiento con su oferta
     * @return DTO
     */
    public static SeguimientoDto desde(ServicioSeguimientos.Vista vista) {
        Seguimiento s = vista.detalle().seguimiento();
        Oferta oferta = vista.detalle().oferta();
        String codigo = vista.detalle().codigoMarketplace();
        String vendedor = "googleshopping".equals(codigo) && oferta.getVendedor() != null ? oferta.getVendedor()
                : ServicioBusqueda.nombreVisible(codigo);
        EnlaceCompra enlace = EnlaceCompra.de(oferta.getUrlDestino());
        return new SeguimientoDto(oferta.getId(), oferta.getTitulo(), codigo, vendedor,
                EnlaceCompra.esSeguro(oferta.getUrlImagen()) ? oferta.getUrlImagen() : null, oferta.getUrlDestino(),
                new OfertaDto.EnlaceDto(enlace.tipo().name().toLowerCase(Locale.ROOT), enlace.textoBoton(),
                        enlace.dominio(), enlace.llevaALaTienda()),
                s.getUltimoPrecio() != null ? s.getUltimoPrecio() : oferta.getPrecioActual(), oferta.getMoneda(),
                s.getTipoAviso().name().toLowerCase(Locale.ROOT), s.getPrecioObjetivo(),
                s.getEstado().name().toLowerCase(Locale.ROOT), s.getUltimaComprobacion(), s.getProximaComprobacion(),
                s.getResultado().name().toLowerCase(Locale.ROOT), textoResultado(s), s.getCreadoEn(),
                vista.avisos().stream().map(SeguimientoDto::aviso).toList());
    }

    private static AvisoDto aviso(AvisoSeguimiento aviso) {
        String texto = switch (aviso.getEstado()) {
            case ENVIADO -> "Aceptado por el servicio de correo";
            case PENDIENTE -> aviso.getUltimoError() == null ? "Por enviar"
                    : "Por enviar: " + aviso.getUltimoError();
            case FALLIDO -> "No se pudo enviar" + (aviso.getUltimoError() == null ? "" : ": " + aviso.getUltimoError());
            case DESCARTADO -> "No se envió: " + (aviso.getUltimoError() == null ? "ya no aplicaba" : aviso.getUltimoError());
        };
        return new AvisoDto(aviso.getTipo().name().toLowerCase(Locale.ROOT), aviso.getEstado().name().toLowerCase(Locale.ROOT),
                texto, aviso.getPrecioAnterior(), aviso.getPrecioActual(), aviso.getCreadoEn(), aviso.getEnviadoEn());
    }

    static String textoResultado(Seguimiento s) {
        return switch (s.getResultado()) {
            case SIN_COMPROBAR -> "Todavía no hemos vuelto a revisar el precio.";
            case REFERENCIA -> "Revisamos el precio y lo guardamos para compararlo en las próximas revisiones.";
            case SIN_CAMBIO -> "En la última revisión el precio no bajó.";
            case SUBIO -> "En la última revisión el precio subió.";
            case BAJO -> "En la última revisión el precio bajó y le avisamos por correo.";
            case OBJETIVO_ALCANZADO -> "El precio llegó a su objetivo y le avisamos por correo.";
            case NO_ENCONTRADA -> "La tienda no mostró esta oferta en la última revisión. Seguiremos intentando.";
            case VARIANTE_DISTINTA -> "La oferta cambió de vendedor, de condición o de versión. No lo tomamos como un "
                    + "descuento y no le avisamos.";
            case PRECIO_DUDOSO -> "La tienda mostró un precio que no pudimos verificar (por ejemplo \"desde\" o por "
                    + "cuotas). No lo usamos.";
            case ERROR_TIENDA -> "La tienda no respondió en la última revisión. Volveremos a intentar.";
        };
    }
}
