package com.chumbok.uaa.security;

import com.chumbok.security.util.EncryptionKeyUtil;
import com.chumbok.testable.common.DateUtil;
import com.chumbok.uaa.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates auth access tokens.
 */
@Slf4j
@Service
@AllArgsConstructor
public class AuthTokenBuilder {

    private final AuthJwtProperties authJwtProperties;
    private final EncryptionKeyUtil encryptionKeyUtil;
    private final DateUtil dateUtil;
    private final JwtUtil jwtUtil;

    /**
     * Creates access tokens from Authentication.
     *
     * @param authentication
     * @return token.
     */
    public String createAccessToken(Authentication authentication) {

        String principal = (String) authentication.getPrincipal();

        if (StringUtils.isBlank(principal)) {
            throw new IllegalStateException("Authentication principle can not be null or empty.");
        }

        String[] orgTenantUsername = principal.split(String.valueOf(Character.LINE_SEPARATOR));

        if (orgTenantUsername == null || orgTenantUsername.length != 3) {
            throw new IllegalStateException(
                    String.format("Authentication principle[%s] should contain org, tenant and username.", principal));
        }

        String org = orgTenantUsername[0];
        String tenant = orgTenantUsername[1];
        String username = orgTenantUsername[2];
        List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());

        if (StringUtils.isBlank(org)) {
            throw new IllegalArgumentException(
                    String.format("Authentication principle[%s] does not contain org.", principal));
        }

        if (StringUtils.isBlank(tenant)) {
            throw new IllegalArgumentException(
                    String.format("Authentication principle[%s] does not contain tenant.", principal));
        }

        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException(
                    String.format("Authentication principle[%s] does not contain username.", principal));
        }

        if (authorities == null || authorities.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Authentication principle[%s] does not contain authorities.", principal));
        }

        Claims claims = Jwts.claims();
        claims.setSubject(username);
        claims.put("org", org);
        claims.put("tenant", tenant);
        claims.put("scopes", authorities.stream().map(s -> s.toString()).collect(Collectors.toList()));

        LocalDateTime currentTime = dateUtil.getCurrentLocalDateTime();
        Date issueDate = Date.from(currentTime.toInstant(ZoneOffset.UTC));
        Date expiration = Date.from(currentTime.plusSeconds(
                authJwtProperties.getTokenExpirationTimeInSecond()).toInstant(ZoneOffset.UTC));

        PrivateKey privateKey = encryptionKeyUtil.loadPrivateKey(authJwtProperties.getTokenSigningPrivateKeyPath());

        return jwtUtil.getJwts(claims, authJwtProperties.getTokenIssuer(), issueDate, expiration, privateKey);
    }

}
