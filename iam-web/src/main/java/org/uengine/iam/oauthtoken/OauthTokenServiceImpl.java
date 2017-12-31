package org.uengine.iam.oauthtoken;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.uengine.iam.oauthclient.OauthClient;
import org.uengine.iam.oauthuser.OauthUser;
import org.uengine.iam.util.JsonUtils;
import org.uengine.iam.util.JwtUtils;

import java.util.*;

@Service
@ConfigurationProperties(prefix = "iam.jwt")
public class OauthTokenServiceImpl implements OauthTokenService {

    private String key;
    private String issuer;
    private long oldRefreshTokenTimeout;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getOldRefreshTokenTimeout() {
        return oldRefreshTokenTimeout;
    }

    public void setOldRefreshTokenTimeout(long oldRefreshTokenTimeout) {
        this.oldRefreshTokenTimeout = oldRefreshTokenTimeout;
    }

    @Autowired
    Environment environment;

    @Override
    public String generateJWTToken(
            OauthUser oauthUser,
            OauthClient oauthClient,
            OauthAccessToken accessToken,
            String claimJson,
            Integer lifetime,
            String type) throws Exception {

        //발급 시간
        Date issueTime = new Date();

        //만료시간
        Date expirationTime = new Date(new Date().getTime() + lifetime * 1000);

        //콘텍스트 설정
        Map context = new HashMap();
        context.put("clientKey", oauthClient.getClientKey());
        context.put("type", accessToken.getType());
        context.put("scopes", accessToken.getScopes());
        context.put("refreshToken", accessToken.getRefreshToken());

        if (type.equals("user")) {
            context.put("userName", oauthUser.getUserName());
            Map<String, Object> userMap = JsonUtils.convertClassToMap(oauthUser);

            //remove unused fields.
            userMap.remove("userPassword");
            context.put("user", userMap);
        }

        //클라이언트의 콘텍스트 필수 항목만 context 에 집어넣는다.
        String[] requiredContext = oauthClient.getRequiredContext();
        List<String> contextList = Arrays.asList(requiredContext);

        //ALL 일 경우는 모두 포함.
        //ALL 이 없는 경우는 requiredContext 에 없다면 콘텍스트에서 제외한다.
        if (!contextList.contains("ALL")) {
            Object[] keyArray = context.keySet().toArray();
            for (int i = 0; i < keyArray.length; i++) {
                if (!contextList.contains(keyArray[i])) {
                    context.remove(keyArray[i]);
                }
            }
        }

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        JWTClaimsSet claimsSet = builder
                .issuer(issuer)
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .claim("context", context)
                .claim("claim", StringUtils.isEmpty(claimJson) ? new HashMap<>() : JsonUtils.marshal(claimJson))
                .build();

        //알고리즘 판별.
        String algorithm = oauthClient.getJwtAlgorithm();

        if (JWSAlgorithm.RS256.getName().equals(algorithm)) {
            JWSSigner signer = new RSASSASigner(JwtUtils.getRSAPrivateKey());
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        }
        //디폴트는 HS256
        else {
            JWSSigner signer = new MACSigner(key);
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        }
    }
}
