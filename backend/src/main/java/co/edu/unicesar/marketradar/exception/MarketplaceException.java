package co.edu.unicesar.marketradar.exception;

/**
 * Error al consultar la API de un marketplace (fallo de red, respuesta inválida o límite de uso).
 */
public class MarketplaceException extends RuntimeException {

    private final String codigoMarketplace;

    /**
     * Crea la excepción indicando qué marketplace falló.
     *
     * @param codigoMarketplace código del marketplace, por ejemplo {@code aliexpress}
     * @param mensaje descripción técnica del error
     */
    public MarketplaceException(String codigoMarketplace, String mensaje) {
        super(mensaje);
        this.codigoMarketplace = codigoMarketplace;
    }

    /**
     * Crea la excepción indicando qué marketplace falló y la causa original.
     *
     * @param codigoMarketplace código del marketplace
     * @param mensaje descripción técnica del error
     * @param causa excepción original
     */
    public MarketplaceException(String codigoMarketplace, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoMarketplace = codigoMarketplace;
    }

    public String getCodigoMarketplace() {
        return codigoMarketplace;
    }
}
