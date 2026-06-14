package com.eduplatform.common.service;

import com.eduplatform.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 内容审核服务。
 * 用于过滤用户生成内容中的敏感词。
 */
@Slf4j
@Service
public class ContentModerationService {

    /**
     * 基础敏感词列表（实际生产环境应从配置或数据库加载）
     */
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>(Arrays.asList(
            // 这里添加需要过滤的敏感词
            // 实际生产环境应该从配置文件或数据库加载
    ));

    /**
     * 正则模式匹配（用于更复杂的过滤规则）
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 检查内容是否包含敏感信息
     *
     * @param content 待检查的内容
     * @throws BusinessException 如果包含敏感信息
     */
    public void checkOrThrow(String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        // 检查敏感词
        String lowerContent = content.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerContent.contains(word.toLowerCase())) {
                log.warn("内容包含敏感词: {}", maskContent(content));
                throw new BusinessException("内容包含敏感信息，请修改后重试");
            }
        }

        // 检查手机号
        if (PHONE_PATTERN.matcher(content).find()) {
            log.warn("内容包含手机号: {}", maskContent(content));
            throw new BusinessException("内容包含手机号，请修改后重试");
        }

        // 检查身份证号
        if (ID_CARD_PATTERN.matcher(content).find()) {
            log.warn("内容包含身份证号: {}", maskContent(content));
            throw new BusinessException("内容包含身份证号，请修改后重试");
        }
    }

    /**
     * 检查内容是否包含敏感信息（返回结果而不抛异常）
     *
     * @param content 待检查的内容
     * @return true 如果包含敏感信息
     */
    public boolean containsSensitiveContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String lowerContent = content.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerContent.contains(word.toLowerCase())) {
                return true;
            }
        }

        if (PHONE_PATTERN.matcher(content).find()) {
            return true;
        }

        if (ID_CARD_PATTERN.matcher(content).find()) {
            return true;
        }

        return false;
    }

    /**
     * 添加敏感词（运行时动态添加）
     *
     * @param word 敏感词
     */
    public void addSensitiveWord(String word) {
        if (word != null && !word.isBlank()) {
            SENSITIVE_WORDS.add(word.toLowerCase());
            log.info("添加敏感词: {}", word);
        }
    }

    /**
     * 移除敏感词
     *
     * @param word 敏感词
     */
    public void removeSensitiveWord(String word) {
        if (word != null && !word.isBlank()) {
            SENSITIVE_WORDS.remove(word.toLowerCase());
            log.info("移除敏感词: {}", word);
        }
    }

    /**
     * 掩码内容（用于日志记录）
     */
    private String maskContent(String content) {
        if (content == null || content.length() <= 10) {
            return "***";
        }
        return content.substring(0, 5) + "***" + content.substring(content.length() - 5);
    }
}
