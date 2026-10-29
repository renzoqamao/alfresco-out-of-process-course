package com.curso.alfresco.reconcile;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import com.curso.alfresco.service.ConfidentialClassifier;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.RequestPagination;
import org.alfresco.search.model.RequestQuery;
import org.alfresco.search.model.ResultSetPaging;
import org.alfresco.search.model.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Leccion 11: al arrancar, procesa lo que cambio mientras la extension estaba parada.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "curso.reconciliation.enabled", havingValue = "true")
public class ReconciliationRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationRunner.class);
    private static final int PAGE_SIZE = 100;

    private final Checkpoint checkpoint;
    private final SearchApi searchApi;
    private final ConfidentialClassifier classifier;
    private final Duration margin;

    public ReconciliationRunner(Checkpoint checkpoint, SearchApi searchApi, ConfidentialClassifier classifier,
                                @Value("${curso.reconciliation.margin}") Duration margin) {
        this.checkpoint = checkpoint;
        this.searchApi = searchApi;
        this.classifier = classifier;
        this.margin = margin;
    }

    @Override
    public void run(String... args) {
        Optional<ZonedDateTime> since = checkpoint.atStartup();
        if (since.isEmpty()) {
            LOGGER.info("[RECONCILIACION] Sin checkpoint previo: nada que recuperar");
            return;
        }
        // El margen cubre eventos recibidos pero no terminados de procesar antes de la parada
        String from = since.get().minus(margin).toInstant().toString();
        String query = "TYPE:\"cm:content\" AND cm:content.mimetype:\"text/plain\" AND cm:modified:[\"" + from + "\" TO NOW]";
        LOGGER.info("[RECONCILIACION] Buscando cambios desde {}", from);

        int total = 0;
        boolean more = true;
        while (more) {
            SearchRequest request = new SearchRequest()
                    .query(new RequestQuery().language(RequestQuery.LanguageEnum.AFTS).query(query))
                    .paging(new RequestPagination().maxItems(PAGE_SIZE).skipCount(total));
            ResultSetPaging page = searchApi.search(request).getBody();
            page.getList().getEntries().forEach(row -> classifier.classify(row.getEntry().getId()));
            total += page.getList().getEntries().size();
            more = Boolean.TRUE.equals(page.getList().getPagination().isHasMoreItems());
        }
        LOGGER.info("[RECONCILIACION] {} documentos revisados", total);
    }
}
