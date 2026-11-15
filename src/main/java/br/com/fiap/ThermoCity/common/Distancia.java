package br.com.fiap.ThermoCity.common;

/** Distância de Haversine em metros (mesma fórmula usada nas análises do banco). */
public final class Distancia {

    private static final double RAIO_TERRA_M = 6_371_000;

    private Distancia() {
    }

    public static double metros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RAIO_TERRA_M * Math.asin(Math.sqrt(a));
    }
}
