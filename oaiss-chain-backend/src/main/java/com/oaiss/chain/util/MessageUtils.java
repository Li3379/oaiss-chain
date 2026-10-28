package com.oaiss.chain.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * 国际化消息工具类
 * Message Utils for i18n
 *
 * @author OAISS Team
 */
@Component
public class MessageUtils {

    private static MessageSource messageSource;

    private final MessageSource injectedMessageSource;

    @Autowired
    public MessageUtils(MessageSource messageSource) {
        this.injectedMessageSource = messageSource;
    }

    @PostConstruct
    void init() {
        messageSource = injectedMessageSource;
    }

    /**
     * 获取国际化消息
     * @param messageKey 消息键
     * @param args 参数
     * @return 格式化后的消息
     */
    public static String getMessage(String messageKey, Object... args) {
        if (messageSource == null) {
            return messageKey + (args != null && args.length > 0 ? Arrays.toString(args) : "");
        }

        try {
            return messageSource.getMessage(
                messageKey,
                args,
                LocaleContextHolder.getLocale()
            );
        } catch (Exception e) {
            // 降级：返回键名和参数
            return messageKey + (args != null && args.length > 0 ? Arrays.toString(args) : "");
        }
    }

    /**
     * 获取指定语言的消息
     * @param messageKey 消息键
     * @param locale 语言
     * @param args 参数
     * @return 格式化后的消息
     */
    public static String getMessage(String messageKey, Locale locale, Object... args) {
        if (messageSource == null) {
            return messageKey + (args != null && args.length > 0 ? Arrays.toString(args) : "");
        }

        try {
            return messageSource.getMessage(messageKey, args, locale);
        } catch (Exception e) {
            return messageKey + (args != null && args.length > 0 ? Arrays.toString(args) : "");
        }
    }

    /**
     * 获取中文消息
     * @param messageKey 消息键
     * @param args 参数
     * @return 格式化后的消息
     */
    public static String getMessageZh(String messageKey, Object... args) {
        return getMessage(messageKey, Locale.SIMPLIFIED_CHINESE, args);
    }

    /**
     * 获取英文消息
     * @param messageKey 消息键
     * @param args 参数
     * @return 格式化后的消息
     */
    public static String getMessageEn(String messageKey, Object... args) {
        return getMessage(messageKey, Locale.US, args);
    }
}
