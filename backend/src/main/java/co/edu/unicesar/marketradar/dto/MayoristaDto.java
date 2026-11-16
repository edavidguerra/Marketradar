package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.mayorista.CalculadoraMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import co.edu.unicesar.marketradar.service.mayorista.ServicioMayoristas;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Respuestas del apartado "Emprender" (compras al por mayor). Todo subtotal va con {@link ServicioMayoristas#NOTA_SUBTOTAL}: es solo la
 * mercancía; el envío, los impuestos y la entrega en Colombia no se conocen.
 */
public final class MayoristaDto {

    private MayoristaDto() {
    }

    /** Una escala de precio. */
    public record EscalaDto(long minimo, Long maximo, BigDecimal precio) {
        static EscalaDto desde(EscalaPrecio e) {
            return new EscalaDto(e.minimo(), e.maximo(), e.precio());
        }
    }

    /** Una variante. */
    public record VarianteDto(String id, String nombre, BigDecimal precio, Boolean disponible) {
    }

    /** Proveedor. */
    public record ProveedorDto(String nombre, String pais, Integer anios, boolean verificado) {
    }

    /** El cálculo para la cantidad pedida. */
    public record CalculoDto(String estado, String motivo, long cantidad, String unidad, String unidadTexto,
                             EscalaDto escala, BigDecimal precioUnitario, BigDecimal subtotal, List<String> ajustes,
                             String nota) {
        static CalculoDto desde(CalculadoraMayorista.Calculo c) {
            return new CalculoDto(c.estado().name(), c.motivo(), c.cantidadCalculada(), c.unidad().name(),
                    c.unidad().con(c.cantidadCalculada()), c.escala() == null ? null : EscalaDto.desde(c.escala()),
                    c.precioUnitario(), c.subtotal(), c.ajustes(), ServicioMayoristas.NOTA_SUBTOTAL);
        }
    }

    /** Valor aproximado en pesos, con la tasa que se usó. */
    public record PesosDto(BigDecimal valor, String moneda, BigDecimal tasa, LocalDate vigenteDesde, String fuente) {
    }

    /** Una oferta con su cálculo. */
    public record OfertaDto(String id, String titulo, String url, String imagen, ProveedorDto proveedor,
                            boolean patrocinado, Long pedidoMinimo, String unidadPedidoMinimo, String pedidoMinimoTexto,
                            String unidad, String presentacion, List<EscalaDto> escalas, String moneda,
                            String textoPrecio, List<VarianteDto> variantes, OffsetDateTime actualizadoEn,
                            String fuente, CalculoDto calculo, Integer posicion, String motivoFuera,
                            boolean consultoDetalle, PesosDto aproximadoPesos, String relevancia,
                            String motivoRelevancia, boolean empate, boolean preliminar) {

        /**
         * @param r resultado del servicio
         * @param monedaPesos moneda local
         * @return DTO
         */
        public static OfertaDto desde(ServicioMayoristas.Resultado r, String monedaPesos) {
            OfertaMayorista o = r.oferta();
            OfertaMayorista.Presentacion p = o.presentacion();
            return new OfertaDto(o.id(), o.titulo(), o.url(), o.imagen(),
                    new ProveedorDto(o.proveedor(), o.paisProveedor(), o.aniosProveedor(), o.proveedorVerificado()),
                    o.patrocinado(), o.pedidoMinimo(), o.unidadPedidoMinimo().name(),
                    o.pedidoMinimo() == null ? null : o.unidadPedidoMinimo().con(o.pedidoMinimo()), o.unidad().name(),
                    p == null ? null : "Cada " + o.unidad().singular() + " trae " + p.unidadBase().con(p.cantidad()),
                    o.escalas().stream().map(EscalaDto::desde).toList(), o.moneda(), o.textoPrecio(),
                    o.variantes().stream().map(v -> new VarianteDto(v.id(), v.nombre(), v.precio(), v.disponible()))
                            .toList(), o.actualizadoEn(), o.fuente(), CalculoDto.desde(r.calculo()), r.posicion(),
                    r.motivoFuera(), r.consultoDetalle(), r.aproximadoPesos() == null ? null
                    : new PesosDto(r.aproximadoPesos(), monedaPesos, r.tasa().valor(), r.tasa().vigenteDesde(),
                    r.tasa().fuente()), r.relevancia() == null ? null
                    : r.relevancia().name().toLowerCase(java.util.Locale.ROOT), r.motivoRelevancia(), r.empate(),
                    r.preliminar());
        }
    }

    /** Respuesta de la búsqueda. */
    public record BusquedaDto(String termino, int pagina, Integer siguientePagina, long cantidad, String unidad,
                              List<OfertaDto> ofertas, int descartados, List<String> avisos, Instant consultadoEn,
                              String nota, String fuente, int comparables, boolean parcial, int verificadas,
                              String ganadoraId) {

        /**
         * @param b búsqueda
         * @param monedaPesos moneda local
         * @return DTO
         */
        public static BusquedaDto desde(ServicioMayoristas.Busqueda b, String monedaPesos) {
            return new BusquedaDto(b.termino(), b.pagina(), b.siguientePagina(), b.cantidad(), b.unidad().name(),
                    b.resultados().stream().map(r -> OfertaDto.desde(r, monedaPesos)).toList(), b.descartados(),
                    b.avisos(), b.consultadoEn(), ServicioMayoristas.NOTA_SUBTOTAL, "Alibaba (vía API.SH)",
                    b.comparables(), b.parcial(), b.verificadas(), b.ganadoraId());
        }
    }

    /** Unidad que se puede elegir. */
    public record UnidadDto(String codigo, String singular, String plural) {
        /** @return unidades que se pueden pedir */
        public static List<UnidadDto> todas() {
            return java.util.Arrays.stream(UnidadVenta.values()).filter(u -> u != UnidadVenta.DESCONOCIDA)
                    .map(u -> new UnidadDto(u.name(), u.singular(), u.plural())).toList();
        }
    }
}
