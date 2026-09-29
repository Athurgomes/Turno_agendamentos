package br.com.reservas.unit.infra;

import br.com.reservas.unit.domain.Unit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    // RN-02: identifier e unico so entre unidades nao excluidas (V3); id fora dessa condicao nao existe "para o app".
    Optional<Unit> findByIdAndDeletedAtIsNull(UUID id);

    // Resolução em lote (evita N+1 em listagens de reservas de várias unidades).
    List<Unit> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    boolean existsByCondominiumIdAndIdentifierIgnoreCaseAndDeletedAtIsNull(UUID condominiumId, String identifier);

    // GET /reservations?unitIdentifier= (D-53): comparacao sem diferenciar maiusculas.
    Optional<Unit> findByIdentifierIgnoreCaseAndDeletedAtIsNull(String identifier);

    /**
     * `search` (docs/03): numero, identificador ou nome de morador ativo, sem
     * diferenciar acentos/maiusculas (unaccent do Postgres); `block` filtra
     * pelo codigo do bloco quando informado.
     */
    @Query(value = """
        select distinct u.* from unit u
        left join resident r on r.unit_id = u.id and r.deleted_at is null
        where u.condominium_id = :condominiumId and u.deleted_at is null
          and (:block is null or lower(u.block) = lower(:block))
          and (:search is null
            or unaccent(lower(u.number)) like unaccent(lower(concat('%', :search, '%')))
            or unaccent(lower(u.identifier)) like unaccent(lower(concat('%', :search, '%')))
            or unaccent(lower(r.name)) like unaccent(lower(concat('%', :search, '%'))))
        """,
        countQuery = """
        select count(distinct u.id) from unit u
        left join resident r on r.unit_id = u.id and r.deleted_at is null
        where u.condominium_id = :condominiumId and u.deleted_at is null
          and (:block is null or lower(u.block) = lower(:block))
          and (:search is null
            or unaccent(lower(u.number)) like unaccent(lower(concat('%', :search, '%')))
            or unaccent(lower(u.identifier)) like unaccent(lower(concat('%', :search, '%')))
            or unaccent(lower(r.name)) like unaccent(lower(concat('%', :search, '%'))))
        """,
        nativeQuery = true)
    Page<Unit> search(@Param("condominiumId") UUID condominiumId, @Param("search") String search,
        @Param("block") String block, Pageable pageable);
}
