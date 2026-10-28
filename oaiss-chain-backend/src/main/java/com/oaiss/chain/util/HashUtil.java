package com.oaiss.chain.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SHA-256哈希工具类
 * 用于碳核算数据完整性验证
 * <p>
 * 设计文档要求：SHA-256用于碳核算信息传输过程中的数据完整性校验
 *
 * @author OAISS Team
 */
public class HashUtil {

    private HashUtil() {
        // 工具类不允许实例化
    }

    /**
     * 计算SHA-256哈希值（返回十六进制字符串）
     *
     * @param data 原始数据
     * @return SHA-256哈希值（64位十六进制字符串）
     */
    public static String sha256Hex(String data) {
        byte[] hash = computeSha256(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * 计算SHA-256哈希值（返回Base64编码字符串）
     *
     * @param data 原始数据
     * @return SHA-256哈希值（Base64编码）
     */
    public static String sha256Base64(String data) {
        byte[] hash = computeSha256(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 验证数据完整性
     *
     * @param data       原始数据
     * @param expectedHex 期望的SHA-256哈希值（十六进制）
     * @return true=数据完整，false=数据被篡改
     */
    public static boolean verifyIntegrity(String data, String expectedHex) {
        String actualHex = sha256Hex(data);
        return actualHex.equalsIgnoreCase(expectedHex);
    }

    /**
     * 计算字节数组的SHA-256
     */
    private static byte[] computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
