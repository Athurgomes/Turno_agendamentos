package br.com.reservas.shared.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * D-46: garante o bucket S3 na subida da aplicacao (retry curto dentro de
 * {@link S3FileStorage#ensureBucketExists()}; nunca derruba a aplicacao se o
 * S3 ainda estiver subindo).
 *
 * <p>{@code @Order} explicito: depois do {@code BootstrapRunner}
 * ({@code HIGHEST_PRECEDENCE}) e antes do seed do perfil {@code local}
 * (`br.com.reservas.seed.LocalSeedRunner`, FD-1), que faz upload de fotos e
 * precisa do bucket ja existir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class StorageBucketInitializer implements ApplicationRunner {

    private final S3FileStorage storage;

    StorageBucketInitializer(S3FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        storage.ensureBucketExists();
    }
}
