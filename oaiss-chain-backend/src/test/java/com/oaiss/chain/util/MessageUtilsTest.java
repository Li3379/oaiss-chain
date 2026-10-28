package com.oaiss.chain.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageUtils unit tests")
class MessageUtilsTest {

    @AfterEach
    void tearDown() {
        // Reset static field via PostConstruct with null
        new MessageUtils(null).init();
    }

    @Test
    @DisplayName("should fall back to key and args when messageSource missing")
    void shouldFallbackWhenMessageSourceMissing() {
        assertEquals("error.key[arg1, 2]", MessageUtils.getMessage("error.key", "arg1", 2));
        assertEquals("plain.key", MessageUtils.getMessageEn("plain.key"));
    }

    @Test
    @DisplayName("should resolve message with explicit locale and helpers")
    void shouldResolveWithExplicitLocale() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("hello"), any(), eq(Locale.US))).thenReturn("hello-en");
        when(messageSource.getMessage(eq("hello"), any(), eq(Locale.SIMPLIFIED_CHINESE))).thenReturn("hello-zh");

        assertEquals("hello-en", MessageUtils.getMessageEn("hello"));
        assertEquals("hello-zh", MessageUtils.getMessageZh("hello"));
    }

    @Test
    @DisplayName("should fall back when message lookup throws")
    void shouldFallbackWhenLookupFails() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("missing"), any(), eq(Locale.US)))
                .thenThrow(new NoSuchMessageException("missing"));

        assertEquals("missing[x]", MessageUtils.getMessage("missing", Locale.US, "x"));
    }

    @Test
    @DisplayName("should return just key when messageSource missing and no args")
    void shouldReturnJustKeyWhenMessageSourceMissingNoArgs() {
        assertEquals("error.key", MessageUtils.getMessage("error.key"));
    }

    @Test
    @DisplayName("should return just key with locale when messageSource missing and no args")
    void shouldReturnJustKeyWithLocaleWhenMessageSourceMissingNoArgs() {
        assertEquals("error.key", MessageUtils.getMessage("error.key", Locale.US));
    }

    @Test
    @DisplayName("should fall back with empty args array")
    void shouldFallbackWithEmptyArgsArray() {
        assertEquals("error.key", MessageUtils.getMessage("error.key"));
    }

    @Test
    @DisplayName("should resolve message from LocaleContextHolder path")
    void shouldResolveFromLocaleContextHolderPath() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("hello"), any(), any(java.util.Locale.class)))
                .thenReturn("hello-resolved");

        assertEquals("hello-resolved", MessageUtils.getMessage("hello"));
    }

    @Test
    @DisplayName("should fall back when LocaleContextHolder path throws exception")
    void shouldFallbackWhenLocaleContextPathThrows() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("broken"), any(), any(java.util.Locale.class)))
                .thenThrow(new RuntimeException("broken"));

        assertEquals("broken[arg]", MessageUtils.getMessage("broken", "arg"));
    }

    @Test
    @DisplayName("should fall back when explicit locale lookup throws with empty args")
    void shouldFallbackWhenExplicitLocaleLookupThrowsNoArgs() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("missing2"), any(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenThrow(new NoSuchMessageException("missing2"));

        assertEquals("missing2", MessageUtils.getMessage("missing2", Locale.SIMPLIFIED_CHINESE));
    }

    @Test
    @DisplayName("getMessageZh should resolve Chinese locale message")
    void getMessageZh_shouldResolveChineseLocale() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("greeting"), any(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenReturn("你好");

        assertEquals("你好", MessageUtils.getMessageZh("greeting"));
    }

    @Test
    @DisplayName("getMessageEn should resolve English locale message")
    void getMessageEn_shouldResolveEnglishLocale() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("greeting"), any(), eq(Locale.US)))
                .thenReturn("Hello");

        assertEquals("Hello", MessageUtils.getMessageEn("greeting"));
    }

    @Test
    @DisplayName("getMessage with null args should return just key")
    void getMessage_withNullArgs_returnsJustKey() {
        new MessageUtils(null).init();
        assertEquals("key", MessageUtils.getMessage("key", (Object[]) null));
    }

    @Test
    @DisplayName("getMessage with locale and null args should return just key")
    void getMessage_withLocaleNullArgs_returnsJustKey() {
        new MessageUtils(null).init();
        assertEquals("key", MessageUtils.getMessage("key", Locale.US, (Object[]) null));
    }

    @Test
    @DisplayName("non-locale exception path with null args returns just key")
    void nonLocaleExceptionPath_withNullArgs_returnsKey() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("broken"), isNull(), any(java.util.Locale.class)))
                .thenThrow(new RuntimeException("broken"));

        assertEquals("broken", MessageUtils.getMessage("broken", (Object[]) null));
    }

    @Test
    @DisplayName("non-locale exception path with empty args returns just key")
    void nonLocaleExceptionPath_withEmptyArgs_returnsKey() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("broken"), any(), any(java.util.Locale.class)))
                .thenThrow(new RuntimeException("broken"));

        assertEquals("broken", MessageUtils.getMessage("broken"));
    }

    @Test
    @DisplayName("locale exception path with null args returns just key")
    void localeExceptionPath_withNullArgs_returnsKey() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("broken"), isNull(), eq(Locale.US)))
                .thenThrow(new RuntimeException("broken"));

        assertEquals("broken", MessageUtils.getMessage("broken", Locale.US, (Object[]) null));
    }

    @Test
    @DisplayName("messageSource null locale path with args present returns key and args")
    void messageSourceNull_localePath_withArgsPresent() {
        new MessageUtils(null).init();
        assertEquals("key[arg1, 2]", MessageUtils.getMessage("key", Locale.US, "arg1", 2));
    }

    @Test
    @DisplayName("messageSource set locale success with null args returns resolved message")
    void messageSourceSet_localeSuccess_withNullArgs() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("hello"), isNull(), eq(Locale.US)))
                .thenReturn("hello-en");

        assertEquals("hello-en", MessageUtils.getMessage("hello", Locale.US, (Object[]) null));
    }

    @Test
    @DisplayName("getMessageEn with messageSource set and exception falls back to key")
    void getMessageEn_withMessageSourceSet_exception_fallbackToKey() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("err_en"), any(), eq(Locale.US)))
                .thenThrow(new NoSuchMessageException("err_en"));

        assertEquals("err_en", MessageUtils.getMessageEn("err_en"));
    }

    @Test
    @DisplayName("getMessageZh with messageSource set and exception falls back to key")
    void getMessageZh_withMessageSourceSet_exception_fallbackToKey() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("err_zh"), any(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenThrow(new NoSuchMessageException("err_zh"));

        assertEquals("err_zh", MessageUtils.getMessageZh("err_zh"));
    }

    @Test
    @DisplayName("getMessage non-locale success path with messageSource set and args")
    void getMessage_nonLocaleSuccess_withArgs() {
        MessageSource messageSource = mock(MessageSource.class);
        new MessageUtils(messageSource).init();

        when(messageSource.getMessage(eq("hello"), any(), any(java.util.Locale.class)))
                .thenReturn("hello-world");

        assertEquals("hello-world", MessageUtils.getMessage("hello", "world"));
    }

    @Test
    @DisplayName("getMessage non-locale null source with empty array args returns key")
    void getMessage_nonLocale_nullSource_emptyArrayArgs() {
        new MessageUtils(null).init();
        assertEquals("key", MessageUtils.getMessage("key", new Object[]{}));
    }

    @Test
    @DisplayName("getMessage locale null source with empty array args returns key")
    void getMessage_locale_nullSource_emptyArrayArgs() {
        new MessageUtils(null).init();
        assertEquals("key", MessageUtils.getMessage("key", Locale.US, new Object[]{}));
    }
}
