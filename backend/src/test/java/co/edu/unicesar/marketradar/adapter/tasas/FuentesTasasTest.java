package co.edu.unicesar.marketradar.adapter.tasas;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FuentesTasasTest {

    @Test
    void leeLaTrmDeDatosAbiertos() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://localhost/trm")).andRespond(withSuccess("""
                [{"valor":"4153.27","unidad":"COP","vigenciadesde":"2026-10-28T00:00:00.000",
                  "vigenciahasta":"2026-10-28T00:00:00.000"}]""", MediaType.APPLICATION_JSON));

        List<TasaCambio> tasas = new FuenteTrmBancoRepublica(builder.build(), PropiedadesPrueba.crear()).consultar();

        assertEquals(1, tasas.size());
        assertEquals("USD", tasas.get(0).monedaOrigen());
        assertEquals(new BigDecimal("4153.27"), tasas.get(0).valor());
        assertEquals(LocalDate.of(2026, 10, 28), tasas.get(0).vigenteDesde());
    }

    @Test
    void trmVaciaEsUnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("http://localhost/trm"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        FuenteTrmBancoRepublica fuente = new FuenteTrmBancoRepublica(builder.build(), PropiedadesPrueba.crear());

        assertThrows(IllegalStateException.class, fuente::consultar);
    }

    @Test
    void calculaPesosPorEuroConBaseEnDolares() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo("http://localhost/tasas"))
                .andRespond(withSuccess("""
                        {"result":"success","base_code":"USD","rates":{"USD":1,"COP":4000,"EUR":0.8}}""",
                        MediaType.APPLICATION_JSON));

        List<TasaCambio> tasas = new FuenteTasasInternacionales(builder.build(), PropiedadesPrueba.crear()).consultar();

        TasaCambio euro = tasas.stream().filter(t -> t.monedaOrigen().equals("EUR")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5000.0000"), euro.valor());
    }
}
