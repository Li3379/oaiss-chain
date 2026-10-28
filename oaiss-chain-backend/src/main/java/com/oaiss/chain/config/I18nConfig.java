package com.oaiss.chain.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * 国际化配置
 * i18n Configuration
 *
 * @author OAISS Team
 */
@Configuration
public class I18nConfig {

    /**
     * 配置国际化消息源
     * Configure MessageSource for i18n
     *
     * @return MessageSource
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        // 设置消息文件基础名（不含语言后缀和扩展名）
        messageSource.setBasenames("classpath:i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 设置缓存时间（秒），开发环境设为0便于调试
        messageSource.setCacheSeconds(0);
        // 找不到消息时返回键名
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }
}
