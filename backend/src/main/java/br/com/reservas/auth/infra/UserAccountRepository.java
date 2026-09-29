package br.com.reservas.auth.infra;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    // RF-AUT-01: o campo de login aceita usuario (UNIT) ou e-mail (ADMIN/SYNDIC),
    // sem diferenciar maiusculas/minusculas.
    @Query("select u from UserAccount u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    Optional<UserAccount> findByLogin(@Param("login") String login);

    boolean existsByCondominiumIdAndRole(UUID condominiumId, Role role);

    // RN-06/RF-UNI-07: e-mail e unico globalmente (indice de V2), nao so por condominio.
    @Query("select case when count(u) > 0 then true else false end from UserAccount u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    // F2: modulo unit precisa da conta ligada a unidade (reset de senha, desativacao,
    // transferencia de titularidade) sem acessar o repositorio diretamente (usa AccountService).
    Optional<UserAccount> findByUnitId(UUID unitId);

    List<UserAccount> findByCondominiumIdAndRoleOrderByDisplayNameAsc(UUID condominiumId, Role role);
}
