package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.PqrDao;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.dto.ResumenAdministradorDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cifras generales para el panel del administrador.
 */
@Service
public class ServicioAdministracion {

    private final PqrDao pqrDao;
    private final UsuarioDao usuarioDao;

    public ServicioAdministracion(PqrDao pqrDao, UsuarioDao usuarioDao) {
        this.pqrDao = pqrDao;
        this.usuarioDao = usuarioDao;
    }

    /**
     * @return PQR por estado y cantidad de usuarios registrados
     */
    public ResumenAdministradorDto resumen() {
        List<Pqr> todas = pqrDao.listarTodos();
        Map<EstadoPqr, Long> porEstado = todas.stream()
                .collect(Collectors.groupingBy(Pqr::getEstado, Collectors.counting()));
        long usuarios = usuarioDao.listarTodos().stream().filter(u -> u.getRol() == Rol.USUARIO).count();
        Function<EstadoPqr, Long> contar = estado -> porEstado.getOrDefault(estado, 0L);
        return new ResumenAdministradorDto(contar.apply(EstadoPqr.RADICADA), contar.apply(EstadoPqr.EN_PROCESO),
                contar.apply(EstadoPqr.RESPONDIDA), usuarios);
    }
}
