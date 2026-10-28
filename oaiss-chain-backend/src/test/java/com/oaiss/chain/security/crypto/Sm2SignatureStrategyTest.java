package com.oaiss.chain.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sm2SignatureStrategyTest {

    private Sm2SignatureStrategy sm2Strategy;

    @BeforeEach
    void setUp() {
        sm2Strategy = new Sm2SignatureStrategy();
    }

    @Test
    @DisplayName("SM2策略元数据与算法标识断言")
    void testAlgorithmMetadata() {
        assertEquals("SM2", sm2Strategy.getAlgorithmType());
        assertEquals("SM3withSM2", sm2Strategy.getSignatureAlgorithm());
    }

    @Test
    @DisplayName("SM2密钥生成与公私钥Base64编解码互转")
    void testKeyPairGenerationAndCodec() {
        KeyPair keyPair = sm2Strategy.generateKeyPair();
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        String pubBase64 = sm2Strategy.encodePublicKey(keyPair.getPublic());
        String privBase64 = sm2Strategy.encodePrivateKey(keyPair.getPrivate());
        assertNotNull(pubBase64);
        assertNotNull(privBase64);

        PublicKey decodedPub = sm2Strategy.decodePublicKey(pubBase64);
        PrivateKey decodedPriv = sm2Strategy.decodePrivateKey(privBase64);
        assertNotNull(decodedPub);
        assertNotNull(decodedPriv);
        assertEquals(keyPair.getPublic().getAlgorithm(), decodedPub.getAlgorithm());
    }

    @Test
    @DisplayName("SM3withSM2数字签名与正常验签成功")
    void testSignAndVerifySuccess() {
        KeyPair keyPair = sm2Strategy.generateKeyPair();
        String reportData = "{\"enterprise\":\"001\",\"totalEmission\":5200.50,\"scope1\":3000.00}";

        String signature = sm2Strategy.sign(reportData, keyPair.getPrivate());
        assertNotNull(signature);
        assertFalse(signature.isBlank());

        boolean valid = sm2Strategy.verify(reportData, signature, keyPair.getPublic());
        assertTrue(valid, "正常生成的 SM2 签名验证必须通过");
    }

    @Test
    @DisplayName("SM2防篡改验证：数据被微调篡改时验签必须失败")
    void testVerifyTamperedDataFails() {
        KeyPair keyPair = sm2Strategy.generateKeyPair();
        String originalData = "{\"enterprise\":\"001\",\"totalEmission\":5200.50}";
        String tamperedData = "{\"enterprise\":\"001\",\"totalEmission\":1200.50}"; // 造假篡改排放量

        String signature = sm2Strategy.sign(originalData, keyPair.getPrivate());

        boolean valid = sm2Strategy.verify(tamperedData, signature, keyPair.getPublic());
        assertFalse(valid, "数据篡改后验签必须拦截返回 false");
    }

    @Test
    @DisplayName("SM2身份鉴别：伪造第三方公钥验签必须失败")
    void testVerifyWithForgedPublicKeyFails() {
        KeyPair genuinePair = sm2Strategy.generateKeyPair();
        KeyPair attackerPair = sm2Strategy.generateKeyPair();
        String data = "{\"enterprise\":\"001\",\"totalEmission\":5200.50}";

        String genuineSignature = sm2Strategy.sign(data, genuinePair.getPrivate());

        boolean valid = sm2Strategy.verify(data, genuineSignature, attackerPair.getPublic());
        assertFalse(valid, "使用非签名主体的公钥验签必须拦截");
    }
}
