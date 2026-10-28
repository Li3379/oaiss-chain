package com.oaiss.chain.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA密钥工具类单元测试
 * Unit tests for RsaKeyUtil
 */
@DisplayName("RsaKeyUtil 单元测试")
class RsaKeyUtilTest {

    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String publicKeyBase64;
    private String privateKeyBase64;

    @BeforeEach
    void setUp() {
        keyPair = RsaKeyUtil.generateKeyPair();
        publicKey = keyPair.getPublic();
        privateKey = keyPair.getPrivate();
        publicKeyBase64 = RsaKeyUtil.encodeKey(publicKey);
        privateKeyBase64 = RsaKeyUtil.encodeKey(privateKey);
    }

    @Nested
    @DisplayName("密钥生成测试")
    class KeyGenerationTests {

        @Test
        @DisplayName("generateKeyPair - 生成有效的RSA密钥对")
        void generateKeyPair_shouldGenerateValidKeyPair() {
            KeyPair result = RsaKeyUtil.generateKeyPair();

            assertNotNull(result, "密钥对不应为null");
            assertNotNull(result.getPublic(), "公钥不应为null");
            assertNotNull(result.getPrivate(), "私钥不应为null");
            assertEquals("RSA", result.getPublic().getAlgorithm(), "算法应为RSA");
            assertEquals("RSA", result.getPrivate().getAlgorithm(), "算法应为RSA");
        }

        @Test
        @DisplayName("getKeySize - 返回正确的密钥长度2048")
        void getKeySize_shouldReturn2048() {
            assertEquals(2048, RsaKeyUtil.getKeySize(), "密钥长度应为2048");
        }
    }

    @Nested
    @DisplayName("签名与验签测试")
    class SignVerifyTests {

        @Test
        @DisplayName("sign and verify - 有效签名验证成功")
        void signAndVerify_validSignature_returnsTrue() {
            String data = "测试数据 - test data for signing";

            String signature = RsaKeyUtil.sign(data, privateKey);
            boolean result = RsaKeyUtil.verify(data, signature, publicKey);

            assertTrue(result, "签名验证应成功");
            assertNotNull(signature, "签名不应为null");
            assertFalse(signature.isEmpty(), "签名不应为空");
        }

        @Test
        @DisplayName("verify - 无效签名返回false")
        void verify_invalidSignature_returnsFalse() {
            String data = "测试数据";
            String invalidSignature = Base64.getEncoder().encodeToString("invalid-signature".getBytes());

            boolean result = RsaKeyUtil.verify(data, invalidSignature, publicKey);

            assertFalse(result, "无效签名验证应失败");
        }

        @Test
        @DisplayName("verify - 错误数据返回false")
        void verify_wrongData_returnsFalse() {
            String originalData = "原始数据";
            String wrongData = "错误数据";

            String signature = RsaKeyUtil.sign(originalData, privateKey);
            boolean result = RsaKeyUtil.verify(wrongData, signature, publicKey);

            assertFalse(result, "错误数据验证应失败");
        }

        @Test
        @DisplayName("sign - 空字符串可以签名")
        void sign_emptyString_canSign() {
            String data = "";

            String signature = RsaKeyUtil.sign(data, privateKey);
            boolean result = RsaKeyUtil.verify(data, signature, publicKey);

            assertTrue(result, "空字符串签名验证应成功");
        }

        @Test
        @DisplayName("getSignatureAlgorithm - 返回SHA256withRSA")
        void getSignatureAlgorithm_shouldReturnCorrectValue() {
            assertEquals("SHA256withRSA", RsaKeyUtil.getSignatureAlgorithm());
        }
    }

    @Nested
    @DisplayName("密钥编码与解码测试")
    class KeyEncodingTests {

        @Test
        @DisplayName("encodeKey - 公钥正确编码")
        void encodeKey_publicKey_encodesCorrectly() {
            String encoded = RsaKeyUtil.encodeKey(publicKey);

            assertNotNull(encoded, "编码后的公钥不应为null");
            assertFalse(encoded.isEmpty(), "编码后的公钥不应为空");
            // 验证是有效的Base64字符串
            assertDoesNotThrow(() -> Base64.getDecoder().decode(encoded));
        }

        @Test
        @DisplayName("encodeKey - 私钥正确编码")
        void encodeKey_privateKey_encodesCorrectly() {
            String encoded = RsaKeyUtil.encodeKey(privateKey);

            assertNotNull(encoded, "编码后的私钥不应为null");
            assertFalse(encoded.isEmpty(), "编码后的私钥不应为空");
            // 验证是有效的Base64字符串
            assertDoesNotThrow(() -> Base64.getDecoder().decode(encoded));
        }

        @Test
        @DisplayName("decodePublicKey - 正确解码公钥")
        void decodePublicKey_decodesCorrectly() {
            PublicKey decoded = RsaKeyUtil.decodePublicKey(publicKeyBase64);

            assertNotNull(decoded, "解码后的公钥不应为null");
            assertEquals("RSA", decoded.getAlgorithm(), "算法应为RSA");
        }

        @Test
        @DisplayName("decodePublicKey - 无效Base64抛出异常")
        void decodePublicKey_invalidBase64_throwsException() {
            String invalidBase64 = "这不是有效的Base64!!!";

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decodePublicKey(invalidBase64));
        }

        @Test
        @DisplayName("decodePublicKey - 无效密钥格式抛出异常")
        void decodePublicKey_invalidKeyFormat_throwsException() {
            String invalidKey = Base64.getEncoder().encodeToString("invalid-key-bytes".getBytes());

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decodePublicKey(invalidKey));
        }

        @Test
        @DisplayName("decodePrivateKey - 正确解码私钥")
        void decodePrivateKey_decodesCorrectly() {
            PrivateKey decoded = RsaKeyUtil.decodePrivateKey(privateKeyBase64);

            assertNotNull(decoded, "解码后的私钥不应为null");
            assertEquals("RSA", decoded.getAlgorithm(), "算法应为RSA");
        }

        @Test
        @DisplayName("decodePrivateKey - 无效Base64抛出异常")
        void decodePrivateKey_invalidBase64_throwsException() {
            String invalidBase64 = "这不是有效的Base64!!!";

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decodePrivateKey(invalidBase64));
        }

        @Test
        @DisplayName("decodePrivateKey - 无效密钥格式抛出异常")
        void decodePrivateKey_invalidKeyFormat_throwsException() {
            String invalidKey = Base64.getEncoder().encodeToString("invalid-key-bytes".getBytes());

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decodePrivateKey(invalidKey));
        }

        @Test
        @DisplayName("encode and decode - 公钥编解码往返成功")
        void encodeDecodePublicKey_roundTrip_succeeds() {
            String encoded = RsaKeyUtil.encodeKey(publicKey);
            PublicKey decoded = RsaKeyUtil.decodePublicKey(encoded);

            assertArrayEquals(publicKey.getEncoded(), decoded.getEncoded(), "编解码后公钥应一致");
        }

        @Test
        @DisplayName("encode and decode - 私钥编解码往返成功")
        void encodeDecodePrivateKey_roundTrip_succeeds() {
            String encoded = RsaKeyUtil.encodeKey(privateKey);
            PrivateKey decoded = RsaKeyUtil.decodePrivateKey(encoded);

            assertArrayEquals(privateKey.getEncoded(), decoded.getEncoded(), "编解码后私钥应一致");
        }
    }

    @Nested
    @DisplayName("加密与解密测试")
    class EncryptDecryptTests {

        @Test
        @DisplayName("encrypt and decrypt - 加解密往返成功")
        void encryptDecrypt_roundTrip_works() {
            String originalData = "这是一段需要加密的敏感数据 - sensitive data to encrypt";

            String encrypted = RsaKeyUtil.encrypt(originalData, publicKey);
            String decrypted = RsaKeyUtil.decrypt(encrypted, privateKey);

            assertNotNull(encrypted, "加密结果不应为null");
            assertNotEquals(originalData, encrypted, "加密后数据应不同于原始数据");
            assertEquals(originalData, decrypted, "解密后数据应与原始数据一致");
        }

        @Test
        @DisplayName("encrypt and decrypt - 大数据分段加解密成功")
        void encryptDecrypt_largeData_works() {
            // 构建超过245字节的大数据（需要分段加密）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                sb.append("A");
            }
            String largeData = sb.toString();
            assertTrue(largeData.length() > 245, "测试数据应超过单次加密块大小");

            String encrypted = RsaKeyUtil.encrypt(largeData, publicKey);
            String decrypted = RsaKeyUtil.decrypt(encrypted, privateKey);

            assertEquals(largeData, decrypted, "大数据加解密后应与原始数据一致");
        }

        @Test
        @DisplayName("encrypt and decrypt - 中文数据加解密成功")
        void encryptDecrypt_chineseData_works() {
            String chineseData = "中文测试数据：碳报告审核流程、碳排放核算";

            String encrypted = RsaKeyUtil.encrypt(chineseData, publicKey);
            String decrypted = RsaKeyUtil.decrypt(encrypted, privateKey);

            assertEquals(chineseData, decrypted, "中文数据加解密后应与原始数据一致");
        }

        @Test
        @DisplayName("encrypt and decrypt - 特殊字符加解密成功")
        void encryptDecrypt_specialCharacters_works() {
            String specialData = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?\n\t\r";

            String encrypted = RsaKeyUtil.encrypt(specialData, publicKey);
            String decrypted = RsaKeyUtil.decrypt(encrypted, privateKey);

            assertEquals(specialData, decrypted, "特殊字符加解密后应与原始数据一致");
        }

        @Test
        @DisplayName("encrypt - 空字符串可以加密")
        void encrypt_emptyString_canEncrypt() {
            String emptyData = "";

            String encrypted = RsaKeyUtil.encrypt(emptyData, publicKey);
            String decrypted = RsaKeyUtil.decrypt(encrypted, privateKey);

            assertEquals(emptyData, decrypted, "空字符串加解密后应一致");
        }

        @Test
        @DisplayName("encrypt - null数据抛出异常")
        void encrypt_nullData_throwsException() {
            assertThrows(RuntimeException.class, () -> RsaKeyUtil.encrypt(null, publicKey));
        }

        @Test
        @DisplayName("decrypt - 无效数据抛出异常")
        void decrypt_invalidData_throwsException() {
            String invalidEncrypted = "invalid-encrypted-data!!!";

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decrypt(invalidEncrypted, privateKey));
        }

        @Test
        @DisplayName("decrypt - null数据抛出异常")
        void decrypt_nullData_throwsException() {
            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decrypt(null, privateKey));
        }

        @Test
        @DisplayName("decrypt - 错误的密钥解密失败")
        void decrypt_wrongKey_throwsException() {
            String data = "测试数据";
            String encrypted = RsaKeyUtil.encrypt(data, publicKey);

            // 使用另一个密钥对的私钥解密
            KeyPair anotherKeyPair = RsaKeyUtil.generateKeyPair();
            PrivateKey wrongPrivateKey = anotherKeyPair.getPrivate();

            assertThrows(RuntimeException.class, () -> RsaKeyUtil.decrypt(encrypted, wrongPrivateKey));
        }
    }

    @Nested
    @DisplayName("集成测试")
    class IntegrationTests {

        @Test
        @DisplayName("完整流程 - 密钥生成、签名、加密、解密、验签")
        void fullWorkflow_succeeds() {
            // 1. 生成密钥对
            KeyPair newKeyPair = RsaKeyUtil.generateKeyPair();
            PublicKey pub = newKeyPair.getPublic();
            PrivateKey priv = newKeyPair.getPrivate();

            // 2. 编码密钥
            String pubEncoded = RsaKeyUtil.encodeKey(pub);
            String privEncoded = RsaKeyUtil.encodeKey(priv);

            // 3. 解码密钥
            PublicKey decodedPub = RsaKeyUtil.decodePublicKey(pubEncoded);
            PrivateKey decodedPriv = RsaKeyUtil.decodePrivateKey(privEncoded);

            // 4. 签名
            String originalData = "碳报告数据 - Carbon Report Data";
            String signature = RsaKeyUtil.sign(originalData, decodedPriv);

            // 5. 验签
            boolean verified = RsaKeyUtil.verify(originalData, signature, decodedPub);
            assertTrue(verified, "签名验证应成功");

            // 6. 加密
            String encrypted = RsaKeyUtil.encrypt(originalData, decodedPub);

            // 7. 解密
            String decrypted = RsaKeyUtil.decrypt(encrypted, decodedPriv);
            assertEquals(originalData, decrypted, "解密后数据应与原始数据一致");
        }
    }
}
