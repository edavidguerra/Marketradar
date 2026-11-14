package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.correo.CorreoNoEnviadoException;
import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.correo.TransporteCorreo;

import java.util.ArrayList;
import java.util.List;

/** Transporte de correo de prueba: guarda los mensajes en una lista y puede fallar a propósito. */
class TransporteCorreoEnMemoria implements TransporteCorreo {

    final List<MensajeCorreo> enviados = new ArrayList<>();
    boolean configurado = true;
    int fallosPendientes;

    @Override
    public boolean estaConfigurado() {
        return configurado;
    }

    @Override
    public void enviar(MensajeCorreo mensaje) {
        if (fallosPendientes > 0) {
            fallosPendientes--;
            throw new CorreoNoEnviadoException("Fallo simulado");
        }
        enviados.add(mensaje);
    }
}
