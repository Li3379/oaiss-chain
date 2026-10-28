package com.oaiss.chain.security.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 数字签名与密码学策略接口
 * 支撑国际标准 (RSA/AES) 与国家商用密码标准 (SM2/SM3/SM4) 双轨自适应
 *
 * @author OAISS Team
 */
public interface SignatureStrategy {

    /**
     * 算法标识类型 (如 "RSA", "SM2")
     */
    String getAlgorithmType();

    /**
     * 签名算法全名 (如 "SHA256withRSA", "SM3withSM2")
     */
    String getSignatureAlgorithm();

    /**
     * 生成非对称密钥对
     */
    KeyPair generateKeyPair();

    /**
     * 对明文字符串进行数字签名，返回 Base64 签名串
     */
    String sign(String data, PrivateKey privateKey);

    /**
     * 验证数字签名
     */
    boolean verify(String data, String signatureBase64, PublicKey publicKey);

    /**
     * 将公钥对象编码为 Base64
     */
    String encodePublicKey(PublicKey publicKey);

    /**
     * 将私钥对象编码为 Base64
     */
    String encodePrivateKey(PrivateKey privateKey);

    /**
     * 从 Base64 解码公钥
     */
    PublicKey decodePublicKey(String base64);

    /**
     * 从 Base64 解码私钥
     */
    PrivateKey decodePrivateKey(String base64);
}
