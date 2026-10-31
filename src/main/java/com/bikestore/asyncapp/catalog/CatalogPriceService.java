package com.bikestore.asyncapp.catalog;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bikestore.asyncapp.api.InvalidOrderException;

@Service
public class CatalogPriceService {
    private final Map<String, CatalogPrice> prices;

    public CatalogPriceService() {
        Map<String, CatalogPrice> catalog = new LinkedHashMap<>();
        add(catalog, "Mirador", "1340.00");
        add(catalog, "Alerce", "1560.00");
        add(catalog, "Cobalto", "1290.00");
        add(catalog, "Hierro", "1890.00");
        add(catalog, "Abeto", "1420.00");
        add(catalog, "Ruta Clara", "1050.00");
        add(catalog, "Ruta Carmesí", "890.00");
        add(catalog, "Ruta Aurora", "1480.00");
        add(catalog, "Sendero Norte", "920.00");
        add(catalog, "Cerro Rojo", "1120.00");
        add(catalog, "Ciudad Verde", "640.00");
        add(catalog, "Ciudad Gris", "720.00");
        prices = Map.copyOf(catalog);
    }

    public CatalogPrice resolve(String bikeModel) {
        if (bikeModel == null) {
            throw new InvalidOrderException("El modelo de bicicleta es obligatorio");
        }
        CatalogPrice price = prices.get(normalize(bikeModel));
        if (price == null) {
            throw new InvalidOrderException("El modelo no existe en el catálogo: " + bikeModel);
        }
        return price;
    }

    private static void add(Map<String, CatalogPrice> catalog, String name, String price) {
        catalog.put(normalize(name), new CatalogPrice(name, new BigDecimal(price)));
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value.strip(), Normalizer.Form.NFD);
        return Normalizer.normalize(decomposed.replaceAll("\\p{M}", ""), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }
}
