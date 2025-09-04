package br.gov.pb.der.netnotify.service;

import java.security.Key;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Service
public class JwtService {
    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    @Value("${jwt.expiration-time}")
    private long jwtExpiration;

    @Value("${app.timezone}")
    private String TZ;

    private static final String ISSUER = "netnotify-api"; // Emissor do token

    /**
     * Gera um token JWT para o usuário fornecido.
     *
     * @param user Detalhes do usuário para o qual o token será gerado.
     * @return Um token JWT assinado.
     */
    public String generateToken(UserDetails user) {
        try {
            // Define o algoritmo ES256 para criar a assinatura do token passando a
            // chave secreta definida
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
            return JWT.create()
                    .withIssuer(ISSUER) // Define o emissor do token
                    .withIssuedAt(creationDate()) // Define a data de emissão do token
                    .withExpiresAt(expirationDate()) // Define a data de expiração do token
                    .withSubject(user.getUsername()) // Define o assunto do token (neste caso, o nome de usuário)
                    .sign(algorithm); // Assina o token usando o algoritmo especificado
        } catch (JWTCreationException exception) {
            throw new JWTCreationException("Erro ao gerar token.", exception);
        }
    }

    public String getSubjectFromToken(String token) {
        try {
            // Define o algoritmo es256 para verificar a assinatura do token passando a
            // chave secreta definida
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER) // Define o emissor do token
                    .build()
                    .verify(token) // Verifica a validade do token
                    .getSubject(); // Obtém o assunto (neste caso, o nome de usuário) do token
        } catch (JWTVerificationException exception) {
            throw new JWTVerificationException("Token inválido ou expirado.");
        }
    }

    private Instant creationDate() {
        return ZonedDateTime.now(ZoneId.of(TZ)).toInstant();
    }

    private Instant expirationDate() {
        return ZonedDateTime.now(ZoneId.of(TZ)).plusSeconds(jwtExpiration).toInstant();
    }

    public boolean isTokenValid(String token) {
        try {
            // Define o algoritmo es256 para verificar a assinatura do token passando a
            // chave secreta definida
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
            JWT.require(algorithm)
                    .withIssuer(ISSUER) // Define o emissor do token
                    .build()
                    .verify(token); // Verifica a validade do token
            return true; // Se não lançar exceção, o token é válido
        } catch (JWTVerificationException exception) {
            return false; // Se lançar exceção, o token é inválido ou expirado
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    // Obtém as roles do token
    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("roles", List.class);
    }

    // Valida o token JWT
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SignatureException ex) {
            // Assinatura inválida
        } catch (MalformedJwtException ex) {
            // Token malformado
        } catch (ExpiredJwtException ex) {
            // Token expirado
        } catch (UnsupportedJwtException ex) {
            // Token não suportado
        } catch (IllegalArgumentException ex) {
            // Claims vazio
        }
        return false;
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}