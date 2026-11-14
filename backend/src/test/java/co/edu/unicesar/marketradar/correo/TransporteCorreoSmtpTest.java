package co.edu.unicesar.marketradar.correo;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manda correos a un servidor SMTP de prueba (GreenMail) en la misma máquina: nada sale a internet. */
class TransporteCorreoSmtpTest {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

    private static PropiedadesCorreo propiedades(String host, int puerto) {
        return new PropiedadesCorreo(host, puerto, null, null, "avisos@marketradar.test", "Marketradar", false, false,
                5, "http://localhost:8080");
    }

    @Test
    void mandaElCorreoConTextoYHtml() throws Exception {
        TransporteCorreoSmtp transporte = new TransporteCorreoSmtp(propiedades("127.0.0.1", ServerSetupTest.SMTP.getPort()));
        assertTrue(transporte.estaConfigurado());
        transporte.enviar(new MensajeCorreo("ana@example.com", "Bajó el precio: iPhone 13",
                "Precio anterior: $ 3.000.000", "<p>Precio anterior: <strong>$ 3.000.000</strong></p>"));

        assertTrue(SMTP.waitForIncomingEmail(5000, 1));
        MimeMessage recibido = SMTP.getReceivedMessages()[0];
        assertEquals("Bajó el precio: iPhone 13", recibido.getSubject());
        assertEquals("ana@example.com", recibido.getAllRecipients()[0].toString());
        assertTrue(recibido.getFrom()[0].toString().contains("avisos@marketradar.test"));
        String cuerpo = GreenMailUtil.getBody(recibido);
        assertTrue(cuerpo.contains("text/plain") && cuerpo.contains("text/html"), "Trae las dos versiones");
    }

    @Test
    void sinHostNoEstaConfiguradoYNoFingeElEnvio() {
        TransporteCorreoSmtp transporte = new TransporteCorreoSmtp(propiedades("", 587));
        assertFalse(transporte.estaConfigurado());
        assertThrows(CorreoNoEnviadoException.class,
                () -> transporte.enviar(new MensajeCorreo("ana@example.com", "a", "b", "<p>b</p>")));
        assertEquals(0, SMTP.getReceivedMessages().length);
    }

    @Test
    void siElServidorNoRespondeElErrorNoTraeDatosDeLaCuenta() {
        PropiedadesCorreo caido = new PropiedadesCorreo("127.0.0.1", 1, "usuario-secreto", "clave-secreta",
                "avisos@marketradar.test", null, false, false, 2, null);
        CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class, () -> new TransporteCorreoSmtp(caido)
                .enviar(new MensajeCorreo("ana@example.com", "a", "b", "<p>b</p>")));
        assertFalse(e.getMessage().contains("usuario-secreto"));
        assertFalse(e.getMessage().contains("clave-secreta"));
        assertFalse(caido.toString().contains("clave-secreta"));
    }
}
