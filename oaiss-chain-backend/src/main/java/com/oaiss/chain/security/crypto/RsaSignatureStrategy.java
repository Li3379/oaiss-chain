package com.oaiss.chain.security.crypto;

import com.oaiss.chain.util.RsaKeyUtil;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 国际通用标准 RSA 数字签名策略实现
 * 算法规格: RSA-2048, SHA256withRSA
 *
 * @author OAISS Team
 */
@Component("rsaSignatureStrategy")
public class RsaSignatureStrategy implements SignatureStrategy {

    @Override
    public String getAlgorithmType() {
        return "RSA";
    }

    @Override
    public String getSignatureAlgorithm() {
        return RsaKeyUtil.getSignatureAlgorithm();
    }

    @Override
    public KeyPair generateKeyPair() {
        return RsaKeyUtil.generateKeyPair();
    }

    @Override
    public String sign(String data, PrivateKey privateKey) {
        return RsaKeyUtil.sign(data, privateKey);
    }

    @Override
    public boolean verify(String data, String signatureBase64, PublicKey publicKey) {
        return RsaKeyUtil.verify(data, signatureBase64, publicKey);
    }

    @Override
    public String encodePublicKey(PublicKey publicKey) {
        return RsaKeyUtil.encodeKey(publicKey);
    }

    @Override
    public String encodePrivateKey(PrivateKey privateKey) {
        return RsaKeyUtil.encodeKey(privateKey);
    }

    @Override
    public PublicKey decodePublicKey(String base64) {
        return RsaKeyUtil.decodePublicKey(base64);
    }

    @Override
    public PrivateKey decodePrivateKey(String base64) {
        return RsaKeyUtil.decodePrivateKey(base64);
    }
}
