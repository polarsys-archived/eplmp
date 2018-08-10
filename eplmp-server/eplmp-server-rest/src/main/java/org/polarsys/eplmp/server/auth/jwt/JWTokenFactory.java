/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.auth.jwt;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.sharing.SharedEntity;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.security.Key;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This JWTokenFactory class is responsible for JWT tokens creation
 *
 * @author Morgan Guimard
 */
public class JWTokenFactory {

    private static final Logger LOGGER = Logger.getLogger(JWTokenFactory.class.getName());
    private static final String ALG = AlgorithmIdentifiers.HMAC_SHA256;
    private static final Long JWT_TOKEN_EXPIRES_TIME = 60 * 60 *3l; // 3 hours token lifetime, to prevent timeout when uploading
    private static final Long JWT_TOKEN_REFRESH_BEFORE = 3 * 60l; // Deliver new token 3 minutes before expiration

    private static final String SUBJECT_LOGIN = "login";
    private static final String SUBJECT_GROUP_NAME = "groupName";
    private static final String SHARED_ENTITY_UUID = "uuid";
    private static final String ENTITY_KEY = "key";

    private JWTokenFactory() {
    }

    public static String createAuthToken(Key key, UserGroupMapping userGroupMapping) {
        JsonObjectBuilder subjectBuilder = Json.createObjectBuilder();
        subjectBuilder.add(SUBJECT_LOGIN, userGroupMapping.getLogin());
        subjectBuilder.add(SUBJECT_GROUP_NAME, userGroupMapping.getGroupName());
        JsonObject build = subjectBuilder.build();
        return createToken(key, build);
    }

    public static String createSharedEntityToken(Key key, SharedEntity sharedEntity) {
        JsonObjectBuilder subjectBuilder = Json.createObjectBuilder();
        subjectBuilder.add(SHARED_ENTITY_UUID, sharedEntity.getUuid());
        JsonObject build = subjectBuilder.build();
        return createToken(key, build);
    }

    private static String createToken(Key key, JsonObject jsonClaims) {

        JwtClaims claims = new JwtClaims();
        claims.setSubject(jsonClaims.toString());
        claims.setIssuedAtToNow();
        claims.setExpirationTime(NumericDate.fromSeconds(NumericDate.now().getValue() + JWT_TOKEN_EXPIRES_TIME));

        JsonWebSignature jws = new JsonWebSignature();
        jws.setDoKeyValidation(false);
        jws.setPayload(claims.toJson());
        jws.setKey(key);
        jws.setAlgorithmHeaderValue(ALG);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static JWTokenUserGroupMapping validateAuthToken(Key key, String jwt) {

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setVerificationKey(key)
                .setRelaxVerificationKeyValidation()
                .build();

        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            String subject = jwtClaims.getSubject();

            try (JsonReader reader = Json.createReader(new StringReader(subject))) {
                JsonObject subjectObject = reader.readObject(); // JsonParsingException
                String login = subjectObject.getString(SUBJECT_LOGIN); // Npe
                String groupName = subjectObject.getString(SUBJECT_GROUP_NAME); // Npe

                if (login != null && !login.isEmpty() && groupName != null && !groupName.isEmpty()) {
                    return new JWTokenUserGroupMapping(jwtClaims, new UserGroupMapping(login, groupName));
                }
            }


        } catch (InvalidJwtException | MalformedClaimException | JsonParsingException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Cannot validate jwt token", e);
        }

        return null;

    }

    public static String validateSharedResourceToken(Key key, String jwt) {

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setVerificationKey(key)
                .setRelaxVerificationKeyValidation()
                .build();

        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            String subject = jwtClaims.getSubject();
            try (JsonReader reader = Json.createReader(new StringReader(subject))) {
                JsonObject subjectObject = reader.readObject(); // JsonParsingException
                return subjectObject.getString(SHARED_ENTITY_UUID); // Npe
            }
        } catch (InvalidJwtException | MalformedClaimException | JsonParsingException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Cannot validate jwt token", e);
        }

        return null;

    }

    public static boolean isJWTValidBefore(Key key, int seconds, String authorizationString) {
        JWTokenUserGroupMapping jwTokenUserGroupMapping = validateAuthToken(key, authorizationString);
        if (jwTokenUserGroupMapping != null) {
            try {
                NumericDate issuedAt = jwTokenUserGroupMapping.getClaims().getIssuedAt();
                issuedAt.addSeconds(seconds);
                return NumericDate.now().isBefore(issuedAt);
            } catch (MalformedClaimException e) {
                return false;
            }
        }
        return false;
    }

    public static void refreshTokenIfNeeded(Key key, HttpServletResponse response, JWTokenUserGroupMapping jwTokenUserGroupMapping) {

        try {
            NumericDate expirationTime = jwTokenUserGroupMapping.getClaims().getExpirationTime();

            if (NumericDate.now().getValue() + JWT_TOKEN_REFRESH_BEFORE >= expirationTime.getValue()) {
                UserGroupMapping userGroupMapping = jwTokenUserGroupMapping.getUserGroupMapping();
                response.addHeader("jwt", createAuthToken(key, userGroupMapping));
            }

        } catch (MalformedClaimException e) {
            LOGGER.log(Level.SEVERE, "Cannot get expiration time from claims", e);
        }

    }

    public static String createEntityToken(Key key, String entityKey) {
        JsonObjectBuilder subjectBuilder = Json.createObjectBuilder();
        subjectBuilder.add(ENTITY_KEY, entityKey);
        JsonObject build = subjectBuilder.build();
        return createToken(key, build);
    }

    public static String validateEntityToken(Key key, String jwt) {

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setVerificationKey(key)
                .setRelaxVerificationKeyValidation()
                .build();

        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            String subject = jwtClaims.getSubject();
            try (JsonReader reader = Json.createReader(new StringReader(subject))) {
                JsonObject subjectObject = reader.readObject(); // JsonParsingException
                return subjectObject.getString(ENTITY_KEY); // Npe
            }
        } catch (InvalidJwtException | MalformedClaimException | JsonParsingException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Cannot validate jwt token", e);
        }

        return null;

    }

}
