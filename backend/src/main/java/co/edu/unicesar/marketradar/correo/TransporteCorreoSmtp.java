package co.edu.unicesar.marketradar.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Manda los correos por SMTP con los datos de {@link PropiedadesCorreo}. Sin servidor configurado no manda nada y lo
 * dice: nunca se da por enviado un correo que no salió. Sirve donde la salida SMTP está permitida (por ejemplo en un
 * computador o un servidor propio); en el plan gratis de Render no, porque bloquea los puertos 25, 465 y 587: allí se
 * usa {@link TransporteCorreoHttp}. Lo crea {@link ConfiguracionCorreo}.
 */
public class TransporteCorreoSmtp implements TransporteCorreo {

    private final PropiedadesCorreo propiedades;
    private final JavaMailSenderImpl remitente;

    public TransporteCorreoSmtp(PropiedadesCorreo propiedades) {
        this.propiedades = propiedades;
        this.remitente = propiedades.host() != null && !propiedades.host().isBlank() && propiedades.remitente() != null
                && !propiedades.remitente().isBlank() ? crear(propiedades) : null;
    }

    private static JavaMailSenderImpl crear(PropiedadesCorreo propiedades) {
        JavaMailSenderImpl remitente = new JavaMailSenderImpl();
        remitente.setHost(propiedades.host());
        remitente.setPort(propiedades.puerto());
        remitente.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (propiedades.usuario() != null && !propiedades.usuario().isBlank()) {
            remitente.setUsername(propiedades.usuario());
            remitente.setPassword(propiedades.clave());
        }
        Properties opciones = remitente.getJavaMailProperties();
        String milisegundos = String.valueOf(propiedades.timeoutSegundos() * 1000L);
        opciones.put("mail.smtp.auth", String.valueOf(propiedades.usuario() != null && !propiedades.usuario().isBlank()));
        opciones.put("mail.smtp.starttls.enable", String.valueOf(propiedades.starttls()));
        opciones.put("mail.smtp.starttls.required", String.valueOf(propiedades.starttls()));
        opciones.put("mail.smtp.ssl.enable", String.valueOf(propiedades.ssl()));
        opciones.put("mail.smtp.connectiontimeout", milisegundos);
        opciones.put("mail.smtp.timeout", milisegundos);
        opciones.put("mail.smtp.writetimeout", milisegundos);
        return remitente;
    }

    @Override
    public boolean estaConfigurado() {
        return remitente != null;
    }

    @Override
    public String nombre() {
        return "smtp";
    }

    @Override
    public ResultadoEnvio enviar(MensajeCorreo mensaje) {
        if (remitente == null) {
            throw new CorreoNoEnviadoException("El envío de correos no está configurado (falta SMTP_HOST o CORREO_REMITENTE)");
        }
        try {
            MimeMessage mime = remitente.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            ayudante.setFrom(new InternetAddress(propiedades.remitente(), propiedades.nombreRemitente(),
                    StandardCharsets.UTF_8.name()));
            ayudante.setTo(mensaje.para());
            ayudante.setSubject(mensaje.asunto());
            ayudante.setText(mensaje.texto(), mensaje.html());
            remitente.send(mime);
            return new ResultadoEnvio("smtp", mime.getMessageID());
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // Solo el tipo de error: el mensaje de JavaMail puede traer el usuario del servidor.
            boolean conexion = e instanceof org.springframework.mail.MailSendException
                    && String.valueOf(e.getMessage()).contains("Couldn't connect");
            throw new CorreoNoEnviadoException("El servidor de correo no aceptó el mensaje (" + e.getClass().getSimpleName()
                    + (conexion ? ": no se pudo conectar al puerto " + propiedades.puerto() : "") + ")");
        }
    }
}
