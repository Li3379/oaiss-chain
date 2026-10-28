package com.oaiss.chain.service;

import com.oaiss.chain.dto.CaptchaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码服务
 * 生成图形验证码并缓存验证
 * <p>
 * 设计文档要求（doc01）：
 * - 登录模块需集成图形验证码防止暴力破解
 *
 * @author OAISS Team
 */
@Slf4j
@Service
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long EXPIRE_SECONDS = 300; // 5分钟

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();
    private final Random random = new SecureRandom();

    public CaptchaService() {
        this.redisTemplate = null;
    }

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成验证码
     */
    public CaptchaResponse generateCaptcha() {
        String captchaKey = generateKey();
        String code = generateCode();
        String imageBase64 = generateImage(code);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("captcha:" + captchaKey, code, EXPIRE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis unavailable for storing captcha, fallback to local: {}", e.getMessage());
                captchaStore.put(captchaKey, new CaptchaEntry(code, System.currentTimeMillis()));
            }
        } else {
            captchaStore.put(captchaKey, new CaptchaEntry(code, System.currentTimeMillis()));
        }
        log.info("generateCaptcha: key={}", captchaKey);

        return CaptchaResponse.builder()
                .captchaKey(captchaKey)
                .captchaImage("data:image/png;base64," + imageBase64)
                .expiresIn(EXPIRE_SECONDS)
                .build();
    }

    /**
     * 验证验证码（详细结果）
     */
    public CaptchaVerifyResult verifyCaptchaDetailed(String captchaKey, String userInput) {
        if (captchaKey == null || userInput == null) {
            log.warn("verifyCaptcha: captchaKey or userInput is null");
            return CaptchaVerifyResult.WRONG_CODE;
        }

        String expectedCode = null;
        if (redisTemplate != null) {
            try {
                String redisKey = "captcha:" + captchaKey;
                expectedCode = redisTemplate.opsForValue().get(redisKey);
                if (expectedCode != null) {
                    redisTemplate.delete(redisKey);
                }
            } catch (Exception e) {
                log.warn("Redis unavailable for verifying captcha, fallback to local: {}", e.getMessage());
            }
        }

        if (expectedCode == null) {
            CaptchaEntry entry = captchaStore.remove(captchaKey);
            if (entry == null) {
                log.warn("verifyCaptcha: entry not found for key={}", captchaKey);
                return CaptchaVerifyResult.NOT_FOUND;
            }
            long elapsed = System.currentTimeMillis() - entry.timestamp;
            if (elapsed > TimeUnit.SECONDS.toMillis(EXPIRE_SECONDS)) {
                log.warn("verifyCaptcha: expired for key={}", captchaKey);
                return CaptchaVerifyResult.EXPIRED;
            }
            expectedCode = entry.code;
        }

        boolean match = expectedCode.equalsIgnoreCase(userInput.trim());
        log.debug("verifyCaptcha: key={}, result={}", captchaKey, match);
        return match ? CaptchaVerifyResult.SUCCESS : CaptchaVerifyResult.WRONG_CODE;
    }

    /**
     * 验证验证码（兼容旧接口）
     */
    public boolean verifyCaptcha(String captchaKey, String userInput) {
        return verifyCaptchaDetailed(captchaKey, userInput) == CaptchaVerifyResult.SUCCESS;
    }

    /**
     * 发送短信验证码
     */
    public void sendSmsCode(String phone, Integer type) {
        // 生成6位验证码
        String code = String.format("%06d", random.nextInt(1000000));
        String key = "SMS_" + phone + "_" + type;
        
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("sms:" + key, code, EXPIRE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                captchaStore.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
            }
        } else {
            captchaStore.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
        }
        
        // 实际项目中应调用短信服务发送，日志脱敏
        log.info("SMS code sent to {}: [MASKED] (type: {})", phone, type);
    }

    /**
     * 发送邮箱验证码
     */
    public void sendEmailCode(String email, Integer type) {
        // 生成6位验证码
        String code = String.format("%06d", random.nextInt(1000000));
        String key = "EMAIL_" + email + "_" + type;
        
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("email:" + key, code, 600, TimeUnit.SECONDS);
            } catch (Exception e) {
                captchaStore.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
            }
        } else {
            captchaStore.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
        }
        
        // 实际项目中应调用邮件服务发送，日志脱敏
        log.info("Email code sent to {}: [MASKED] (type: {})", email, type);
    }

    // ==================== 私有方法 ====================

    private String generateKey() {
        return "CAP_" + System.currentTimeMillis() + "_" + random.nextInt(10000);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 边框
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        // 字符
        g.setFont(new Font("Arial", Font.BOLD | Font.ITALIC, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(256)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }

        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("验证码图片生成失败", e);
            return "";
        }
    }

    private static class CaptchaEntry {
        final String code;
        final long timestamp;

        CaptchaEntry(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
    }
}
