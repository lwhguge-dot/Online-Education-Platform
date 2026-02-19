package com.eduplatform.common.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.Vague;

/**
 * 通用 AI 服务接口 (基于 LangChain4j AiServices 声明式调用)
 * 后端智能 Pro Max 技能集成：提供核心 AI 能力支撑
 */
public interface AiService {

    /**
     * 简单的对话接口
     * 
     * @param userMessage 用户输入
     * @return AI 回复
     */
    String chat(String userMessage);

    /**
     * 带系统设置的对话
     * 
     * @param systemMessage 系统提示词
     * @param userMessage   用户输入
     * @return AI 回复
     */
    String chat(@Vague("systemMessage") String systemMessage, @Vague("userMessage") String userMessage);
}
