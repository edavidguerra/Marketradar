package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.dao.AvisoSeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Avisos guardados en memoria, con la misma regla que la tabla: la clave de evento es única. El estado del
 * seguimiento y del correo se fija desde la prueba.
 */
class AvisosEnMemoria implements AvisoSeguimientoDao {

    final List<AvisoSeguimiento> avisos = new ArrayList<>();
    String estadoSeguimiento = "ACTIVO";
    String correoActual = "ana@example.com";
    boolean correoVerificado = true;
    String tipoAviso = "BAJA_PRECIO";
    BigDecimal precioObjetivo;
    OffsetDateTime ultimoProximoIntento;
    private long siguienteId = 1;

    @Override
    public Optional<Long> insertarSiEsNuevo(AvisoSeguimiento aviso) {
        if (avisos.stream().anyMatch(a -> a.getClaveEvento().equals(aviso.getClaveEvento()))) {
            return Optional.empty();
        }
        aviso.setId(siguienteId++);
        aviso.setEstado(AvisoSeguimiento.Estado.PENDIENTE);
        if (aviso.getCreadoEn() == null) {
            aviso.setCreadoEn(OffsetDateTime.now());
        }
        avisos.add(aviso);
        return Optional.of(aviso.getId());
    }

    Pendiente pendiente(AvisoSeguimiento aviso) {
        return new Pendiente(aviso, estadoSeguimiento, correoActual, correoVerificado, "Ana", "iPhone 13 128 GB",
                "https://www.exito.com/iphone-13", "Éxito", "googleshopping", tipoAviso, precioObjetivo);
    }

    @Override
    public List<Pendiente> tomarPendientes(int limite, int minutosApartado) {
        return avisos.stream().filter(a -> a.getEstado() == AvisoSeguimiento.Estado.PENDIENTE).limit(limite)
                .map(this::pendiente).toList();
    }

    @Override
    public Optional<Pendiente> tomar(long id, int minutosApartado) {
        return avisos.stream().filter(a -> a.getId() == id && a.getEstado() == AvisoSeguimiento.Estado.PENDIENTE)
                .findFirst().map(this::pendiente);
    }

    private AvisoSeguimiento porId(long id) {
        return avisos.stream().filter(a -> a.getId() == id).findFirst().orElseThrow();
    }

    @Override
    public void marcarEnviado(long id) {
        AvisoSeguimiento a = porId(id);
        a.setEstado(AvisoSeguimiento.Estado.ENVIADO);
        a.setEnviadoEn(OffsetDateTime.now());
    }

    @Override
    public void registrarFallo(long id, String error, boolean contarIntento, OffsetDateTime proximoIntento) {
        AvisoSeguimiento a = porId(id);
        a.setUltimoError(error);
        if (contarIntento) {
            a.setIntentos(a.getIntentos() + 1);
        }
        ultimoProximoIntento = proximoIntento;
        a.setEstado(proximoIntento == null ? AvisoSeguimiento.Estado.FALLIDO : AvisoSeguimiento.Estado.PENDIENTE);
    }

    @Override
    public void descartar(long id, String motivo) {
        AvisoSeguimiento a = porId(id);
        a.setEstado(AvisoSeguimiento.Estado.DESCARTADO);
        a.setUltimoError(motivo);
    }

    @Override
    public int descartarPendientes(long usuarioId, long ofertaId, String motivo) {
        int n = 0;
        for (AvisoSeguimiento a : avisos) {
            if (a.getUsuarioId() == usuarioId && a.getOfertaId() == ofertaId
                    && a.getEstado() == AvisoSeguimiento.Estado.PENDIENTE) {
                a.setEstado(AvisoSeguimiento.Estado.DESCARTADO);
                n++;
            }
        }
        return n;
    }

    @Override
    public List<AvisoSeguimiento> recientes(long usuarioId, long ofertaId, int limite) {
        return avisos.stream().filter(a -> a.getUsuarioId() == usuarioId && a.getOfertaId() == ofertaId)
                .limit(limite).toList();
    }
}
