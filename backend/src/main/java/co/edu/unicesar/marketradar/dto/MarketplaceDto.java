package co.edu.unicesar.marketradar.dto;

/**
 * Estado de un marketplace para {@code GET /api/v1/marketplaces}.
 *
 * @param codigo código único
 * @param nombre nombre visible
 * @param sitioWeb URL de la tienda
 * @param activo si está activo en la base de datos
 * @param disponible si su adaptador está habilitado y con credenciales
 */
public record MarketplaceDto(String codigo, String nombre, String sitioWeb, boolean activo, boolean disponible) {
}
