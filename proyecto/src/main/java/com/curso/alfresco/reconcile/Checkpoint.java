package com.curso.alfresco.reconcile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Leccion 11: recuerda la fecha del ultimo evento recibido en un fichero.
 * El valor de arranque se lee en el constructor, antes de que el listener reciba eventos nuevos y lo sobrescriba.
 */
@Component
public class Checkpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(Checkpoint.class);

    private final Path file;
    private final ZonedDateTime atStartup;
    private ZonedDateTime last;

    public Checkpoint(@Value("${curso.reconciliation.checkpoint-file}") Path file) throws IOException {
        this.file = file;
        this.atStartup = Files.exists(file) ? ZonedDateTime.parse(Files.readString(file).trim()) : null;
        this.last = atStartup;
    }

    public Optional<ZonedDateTime> atStartup() {
        return Optional.ofNullable(atStartup);
    }

    public synchronized void record(ZonedDateTime time) {
        if (time == null || (last != null && !time.isAfter(last))) {
            return;
        }
        last = time;
        try {
            Files.writeString(file, time.toString());
        } catch (IOException e) {
            LOGGER.warn("No se pudo guardar el checkpoint en {}", file, e);
        }
    }
}
