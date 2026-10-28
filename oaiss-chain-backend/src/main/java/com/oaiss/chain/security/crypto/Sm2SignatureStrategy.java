package com.oaiss.chain.security.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 国家商用密码标准 SM2/SM3 签名策略实现
 * 算法规格: SM2 椭圆曲线 (sm2p256v1), SM3withSM2 杂凑签名
 * 满足《密码法》及等保三级/密评合规要求
 *
 * @author OAISS Team
 */
@Slf4j
@Component("sm2SignatureStrategy")
public class Sm2SignatureStrategy implements SignatureStrategy {

    private static final String BC_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    private static final String ALGORITHM_TYPE = "SM2";
    private static final String SIGNATURE_ALGORITHM = "SM3withSM2";
    private static final String EC_ALGORITHM = "EC";
    private static final String SM2_CURVE_NAME = "sm2p256v1";

    static {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public String getAlgorithmType() {
        return ALGORITHM_TYPE;
    }

    @Override
    public String getSignatureAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    @Override
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(EC_ALGORITHM, BC_PROVIDER);
            ECGenParameterSpec spec = new ECGenParameterSpec(SM2_CURVE_NAME);
            generator.initialize(spec, new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            log.error("SM2 key pair generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("国密 SM2 密钥对生成失败", e);
        }
    }

    @Override
    public String sign(String data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("待签名数据与私钥不能为空");
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            log.error("SM2 signature failed: {}", e.getMessage(), e);
            throw new RuntimeException("国密 SM2 签名失败", e);
        }
    }

    @Override
    public boolean verify(String data, String signatureBase64, PublicKey publicKey) {
        if (data == null || signatureBase64 == null || publicKey == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signBytes);
        } catch (Exception e) {
            log.warn("SM2 signature verification error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String encodePublicKey(PublicKey publicKey) {
        if (publicKey == null) return null;
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    @Override
    public String encodePrivateKey(PrivateKey privateKey) {
        if (privateKey == null) return null;
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    @Override
    public PublicKey decodePublicKey(String base64) {
        if (base64 == null) return null;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance(EC_ALGORITHM, BC_PROVIDER);
            return factory.generatePublic(spec);
        } catch (Exception e) {
            log.error("SM2 public key decode failed: {}", e.getMessage(), e);
            throw new RuntimeException("国密 SM2 公钥解码失败", e);
        }
    }

    @Override
    public PrivateKey decodePrivateKey(String base64) {
        if (base64 == null) return null;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance(EC_ALGORITHM, BC_PROVIDER);
            return factory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("SM2 private key decode failed: {}", e.getMessage(), e);
            throw new RuntimeException("国密 SM2 私钥解码失败", e);
        }
    }
}
