package com.oaiss.chain.util;

import lombok.extern.slf4j.Slf4j;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA密钥工具类
 * RSA Key Utility
 * 
 * 提供RSA密钥对生成、签名、验签、加密、解密功能
 * 用于碳报告数字签名和审核流程中的数据安全传输
 * 
 * @author OAISS Team
 */
@Slf4j
public final class RsaKeyUtil {

    private RsaKeyUtil() {
        // 防止实例化
    }

    /**
     * RSA密钥长度
     */
    private static final int KEY_SIZE = 2048;

    /**
     * RSA算法名称
     */
    private static final String RSA_ALGORITHM = "RSA";

    /**
     * 签名算法（SHA256withRSA）
     */
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * 加密算法（RSA/ECB/PKCS1Padding）
     */
    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";

    /**
     * 最大加密块大小（2048位密钥，PKCS1Padding需要11字节，所以是 2048/8 - 11 = 245）
     */
    private static final int MAX_ENCRYPT_BLOCK = 245;

    /**
     * 最大解密块大小（2048位密钥，2048/8 = 256）
     */
    private static final int MAX_DECRYPT_BLOCK = 256;

    // ==================== 密钥生成 ====================

    /**
     * 生成RSA密钥对
     * Generate RSA key pair (2048-bit)
     * 
     * @return RSA密钥对
     * @throws RuntimeException 如果密钥生成失败
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.error("RSA key pair generation failed: {}", e.getMessage());
            throw new RuntimeException("RSA密钥对生成失败", e);
        }
    }

    // ==================== 签名与验签 ====================

    /**
     * 使用私钥对数据进行签名
     * Sign data with private key using SHA256withRSA
     * 
     * @param data 待签名的数据
     * @param privateKey RSA私钥
     * @return Base64编码的签名结果
     * @throws RuntimeException 如果签名失败
     */
    public static String sign(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            log.error("RSA signing failed: {}", e.getMessage());
            throw new RuntimeException("RSA签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用公钥验证签名
     * Verify signature with public key using SHA256withRSA
     * 
     * @param data 原始数据
     * @param signatureBase64 Base64编码的签名
     * @param publicKey RSA公钥
     * @return 签名是否有效
     */
    public static boolean verify(String data, String signatureBase64, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            log.error("RSA signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 密钥编码与解码 ====================

    /**
     * 将密钥编码为Base64字符串
     * Encode key to Base64 string
     * 
     * @param key RSA密钥（公钥或私钥）
     * @return Base64编码的密钥字符串
     */
    public static String encodeKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从Base64字符串解码公钥
     * Decode public key from Base64 string
     * 
     * @param publicKeyBase64 Base64编码的公钥字符串
     * @return RSA公钥
     * @throws RuntimeException 如果解码失败
     */
    public static PublicKey decodePublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Public key decoding failed: {}", e.getMessage());
            throw new RuntimeException("公钥解码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从Base64字符串解码私钥
     * Decode private key from Base64 string
     * 
     * @param privateKeyBase64 Base64编码的私钥字符串
     * @return RSA私钥
     * @throws RuntimeException 如果解码失败
     */
    public static PrivateKey decodePrivateKey(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Private key decoding failed: {}", e.getMessage());
            throw new RuntimeException("私钥解码失败: " + e.getMessage(), e);
        }
    }

    // ==================== 加密与解密 ====================

    /**
     * 使用公钥加密数据
     * Encrypt data with public key
     * 
     * @param data 待加密的数据
     * @param publicKey RSA公钥
     * @return Base64编码的加密数据
     * @throws RuntimeException 如果加密失败
     */
    public static String encrypt(String data, PublicKey publicKey) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey);
            
            byte[] dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            // 分段加密
            int inputLen = dataBytes.length;
            int offLen = 0;
            byte[] resultBytes = new byte[0];
            
            while (inputLen - offLen > 0) {
                byte[] block;
                if (inputLen - offLen > MAX_ENCRYPT_BLOCK) {
                    block = cipher.doFinal(dataBytes, offLen, MAX_ENCRYPT_BLOCK);
                } else {
                    block = cipher.doFinal(dataBytes, offLen, inputLen - offLen);
                }
                resultBytes = concat(resultBytes, block);
                offLen += MAX_ENCRYPT_BLOCK;
            }
            
            return Base64.getEncoder().encodeToString(resultBytes);
        } catch (Exception e) {
            log.error("RSA encryption failed: {}", e.getMessage());
            throw new RuntimeException("RSA加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用私钥解密数据
     * Decrypt data with private key
     * 
     * @param encryptedDataBase64 Base64编码的加密数据
     * @param privateKey RSA私钥
     * @return 解密后的原始数据
     * @throws RuntimeException 如果解密失败
     */
    public static String decrypt(String encryptedDataBase64, PrivateKey privateKey) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
            
            byte[] dataBytes = Base64.getDecoder().decode(encryptedDataBase64);
            
            // 分段解密
            int inputLen = dataBytes.length;
            int offLen = 0;
            byte[] resultBytes = new byte[0];
            
            while (inputLen - offLen > 0) {
                byte[] block;
                if (inputLen - offLen > MAX_DECRYPT_BLOCK) {
                    block = cipher.doFinal(dataBytes, offLen, MAX_DECRYPT_BLOCK);
                } else {
                    block = cipher.doFinal(dataBytes, offLen, inputLen - offLen);
                }
                resultBytes = concat(resultBytes, block);
                offLen += MAX_DECRYPT_BLOCK;
            }
            
            return new String(resultBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("RSA decryption failed: {}", e.getMessage());
            throw new RuntimeException("RSA解密失败: " + e.getMessage(), e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 合并两个字节数组
     * Concatenate two byte arrays
     */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * 获取签名算法名称
     * Get signature algorithm name
     */
    public static String getSignatureAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    /**
     * 获取密钥长度
     * Get key size
     */
    public static int getKeySize() {
        return KEY_SIZE;
    }
}
