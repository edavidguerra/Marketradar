package co.edu.unicesar.marketradar.adapter.mercadolibre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Guarda el refresh token en un archivo. Sirve donde el disco se conserva (por ejemplo el volumen de Docker).
 */
class AlmacenTokenArchivo implements AlmacenToken {

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenTokenArchivo.class);

    private final Path archivo;

    AlmacenTokenArchivo(Path archivo) {
        this.archivo = archivo;
    }

    @Override
    public Optional<String> leer(String tokenInicial) {
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8).strip();
            return contenido.isEmpty() ? Optional.empty() : Optional.of(contenido);
        } catch (IOException e) {
            LOG.warn("No se pudo leer el refresh token de Mercado Libre en {}", archivo, e);
            return Optional.empty();
        }
    }

    @Override
    public void guardar(String token, String tokenInicial) {
        try {
            Path carpeta = archivo.toAbsolutePath().getParent();
            if (carpeta != null) {
                Files.createDirectories(carpeta);
            }
            Files.writeString(archivo, token, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("No se pudo guardar el refresh token de Mercado Libre en {}. Al reiniciar habrá que generar otro.",
                    archivo, e);
        }
    }
}
