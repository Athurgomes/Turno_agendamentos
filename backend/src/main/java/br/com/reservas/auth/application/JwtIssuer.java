package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** RNF-02/D-42: access token JWT HS256, 15 minutos, claims {@code sub}/{@code role}/{@code ver}. */
@Component
public class JwtIssuer {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtIssuer(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issue(UserAccount account) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(account.getId().toString())
            .claim("role", account.getRole().name())
            .claim("ver", account.getTokenVersion())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TTL))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return ACCESS_TTL.toSeconds();
    }
}
