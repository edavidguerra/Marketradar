package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.TasaCambioDto;
import co.edu.unicesar.marketradar.service.ServicioTasasCambio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tasas de cambio vigentes, para mostrar con qué valor se convirtieron los precios a pesos.
 */
@RestController
@RequestMapping("/api/v1/tasas")
public class ControladorTasas {

    private final ServicioTasasCambio servicioTasas;

    public ControladorTasas(ServicioTasasCambio servicioTasas) {
        this.servicioTasas = servicioTasas;
    }

    @GetMapping
    public List<TasaCambioDto> vigentes() {
        return servicioTasas.listarVigentes().stream().map(TasaCambioDto::desde).toList();
    }
}
