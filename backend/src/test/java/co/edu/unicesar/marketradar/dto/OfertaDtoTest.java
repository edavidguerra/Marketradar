package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OfertaDtoTest {

    @Test
    void sinEnvioInformadoNoHayTotalYElBotonDiceAdondeLleva() {
        OfertaNormalizada oferta = OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("1")
                .titulo("Apple iPhone 13 128 GB").precio(new BigDecimal("2899900")).moneda("COP")
                .vendedor("Alkosto").urlDestino("https://www.google.com.co/search?tbm=shop&q=iphone").build();

        OfertaDto dto = OfertaDto.desde(oferta, 7L, true);

        assertEquals("desconocido", dto.estadoEnvio());
        assertNull(dto.costoEnvio());
        assertNull(dto.totalConfirmado());
        assertEquals(new BigDecimal("2899900"), dto.costoConocido());
        assertEquals("no incluye el envío, que la tienda no informa", dto.faltaParaElTotal());
        assertEquals("No informada", dto.condicionTexto());
        assertNull(dto.condicion());
        assertEquals("busqueda_google", dto.enlace().tipo());
        assertEquals("Buscar esta oferta en Google", dto.enlace().texto());
        assertFalse(dto.enlace().llevaALaTienda());
        assertNull(dto.conversion());
    }

    @Test
    void unPrecioConvertidoMuestraDeDondeSalioLaTasa() {
        TasaCambio tasa = new TasaCambio("USD", new BigDecimal("3912.45"), LocalDate.of(2026, 11, 11),
                "Banco de la República (TRM)");
        OfertaNormalizada oferta = OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("2")
                .titulo("Tensiómetro").precio(new BigDecimal("25.00")).moneda("USD").precioTexto("US$ 25,00")
                .vendedor("Amazon.com").urlDestino("https://www.amazon.com/dp/1").build()
                .convertida(new BigDecimal("97811"), null, "COP", tasa);

        OfertaDto.ConversionDto conversion = OfertaDto.desde(oferta, 8L, false).conversion();

        assertEquals(new BigDecimal("25.00"), conversion.precioOriginal());
        assertEquals("USD", conversion.monedaOriginal());
        assertEquals("US$ 25,00", conversion.precioTexto());
        assertEquals("Banco de la República (TRM)", conversion.fuente());
        assertEquals(LocalDate.of(2026, 11, 11), conversion.fecha());
    }
}
