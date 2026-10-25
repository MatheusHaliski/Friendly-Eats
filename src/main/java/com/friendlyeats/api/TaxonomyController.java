package com.friendlyeats.api;

import com.friendlyeats.application.establishment.TaxonomyCatalog;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Vocabulários da taxonomia (RF7): culinárias, formatos, tipos, preço, estrelas e chips. Leitura pública. */
@RestController
@RequestMapping("/api/v1/taxonomy")
public class TaxonomyController {

    private final TaxonomyCatalog catalog;

    public TaxonomyController(TaxonomyCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<TaxonomyCatalog.Catalog> taxonomy() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(catalog.catalog());
    }
}
