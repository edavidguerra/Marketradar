package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.dao.CredencialDao;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** DAO de credenciales en memoria, para probar sin PostgreSQL. */
class CredencialesEnMemoria implements CredencialDao {

    final Map<String, CredencialGuardada> datos = new HashMap<>();
    boolean falla;

    @Override
    public Optional<CredencialGuardada> buscar(String codigo) {
        if (falla) {
            throw new AccesoDatosException("sin conexión", null);
        }
        return Optional.ofNullable(datos.get(codigo));
    }

    @Override
    public void guardar(String codigo, CredencialGuardada credencial) {
        if (falla) {
            throw new AccesoDatosException("sin conexión", null);
        }
        datos.put(codigo, credencial);
    }
}
