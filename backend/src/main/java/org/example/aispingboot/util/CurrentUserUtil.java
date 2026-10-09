package org.example.aispingboot.util;

import com.auth0.jwt.interfaces.DecodedJWT;

public class CurrentUserUtil {

    public static Long getUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }

    public static Integer getRoleType() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("roleType").asInt();
    }

    public static boolean isAdmin() {
        Integer roleType = getRoleType();
        return roleType != null && roleType == 2;
    }
}