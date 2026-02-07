package com.eduplatform.course.service;

import com.eduplatform.course.entity.BlockedWord;
import com.eduplatform.course.mapper.BlockedWordMapper;
import com.eduplatform.course.vo.BlockedWordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内容安全合规服务 (敏感词过滤)
 * 负责平台各交互环节的内容审核预检查，支持多级词库隔离与动态过滤算法。
 * 
 * 核心机制：
 * 1. 作用域隔离：区分“全局敏感词”（平台通用）与“课程敏感词”（特定课程专属），实现灵活的业务管控。
 * 2. 模糊匹配：采用大小写不敏感的子串包含了检索，确保基本的规避手段无效化。
 * 3. 实时校验：作为评论发布、课程简介编辑等核心环节同步阻塞校验器。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedWordService {

    private final BlockedWordMapper blockedWordMapper;

    /**
     * 检索全平台通用的敏感词词典
     * 用于拦截政治敏感、色情低俗等通用违规词汇。
     */
    public List<BlockedWord> getGlobalWords() {
        try {
            return blockedWordMapper.findGlobalWords();
        } catch (Exception e) {
            log.error("安全合规异常：获取全局屏蔽词库失败", e);
            return List.of();
        }
    }

    /**
     * 检索指定课程专属的敏感词库
     * 常用于课程自治场景，如屏蔽特定竞争对手名称或课程专属禁语。
     */
    public List<BlockedWord> getCourseWords(Long courseId) {
        try {
            return blockedWordMapper.findCourseWords(courseId);
        } catch (Exception e) {
            log.error("安全合规异常：获取课程私有屏蔽词失败, courseId={}", courseId, e);
            return List.of();
        }
    }

    /**
     * 通用词库检索接口
     * 
     * @param scope    作用域标识 (global/course)
     * @param courseId 课程 ID (仅在 scope 为 course 时生效)
     */
    public List<BlockedWord> getWords(String scope, Long courseId) {
        if ("global".equals(scope)) {
            return getGlobalWords();
        } else if ("course".equals(scope) && courseId != null) {
            return getCourseWords(courseId);
        }
        return List.of();
    }

    /**
     * 注册新的屏蔽词条
     * 包含幂等性校验，防止同一作用域下重复注入。
     * 
     * @param word      敏感词原始内容
     * @param scope     生效范围
     * @param courseId  关联课程 (可选)
     * @param createdBy 创建者 ID (审计用)
     */
    @Transactional
    public BlockedWord addWord(String word, String scope, Long courseId, Long createdBy) {
        // 幂等性与冲突校验
        if (blockedWordMapper.checkExists(word, scope, courseId) > 0) {
            throw new RuntimeException("操作失败：该词库条目已存在");
        }

        BlockedWord blockedWord = new BlockedWord();
        blockedWord.setWord(word);
        blockedWord.setScope(scope);
        blockedWord.setCourseId("course".equals(scope) ? courseId : null);
        blockedWord.setCreatedBy(createdBy);
        blockedWord.setCreatedAt(LocalDateTime.now());

        blockedWordMapper.insertWord(blockedWord);
        log.info("审计：敏感词库更新 | 词条: {}, 作用域: {}, 课程上下文: {}", word, scope, courseId);

        return blockedWord;
    }

    /**
     * 物理注销屏蔽词条
     */
    @Transactional
    public void deleteWord(Long id) {
        int affected = blockedWordMapper.deleteWord(id);
        if (affected == 0) {
            throw new RuntimeException("操作失败：词条不存在或已被移除");
        }
        log.info("审计：敏感词库注销 | ID: {}", id);
    }

    /**
     * 核心内容审计算法
     * 算法逻辑：
     * 1. 聚合规则：加载 全局词库 + 当前课程私有词库。
     * 2. 启发式匹配：执行全词大小写不敏感匹配。
     * 
     * @param content  待审核文本内容
     * @param courseId 业务上下文 ID
     * @return 包含命中状态 (hasBlockedWord) 及命中明细 (blockedWords) 的结果集
     */
    public Map<String, Object> checkContent(String content, Long courseId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 聚合当前上下文所有生效的规则
            List<BlockedWord> words = blockedWordMapper.findApplicableWords(courseId);

            // 执行多词匹配 (当前采用 O(N*M) 暴力匹配，若词库增长建议后续重构为 Aho-Corasick 自动机)
            List<String> foundWords = new ArrayList<>();
            String lowerContent = content.toLowerCase();

            for (BlockedWord word : words) {
                if (lowerContent.contains(word.getWord().toLowerCase())) {
                    foundWords.add(word.getWord());
                }
            }

            result.put("hasBlockedWord", !foundWords.isEmpty());
            result.put("blockedWords", foundWords);

        } catch (Exception e) {
            log.error("安全合规异常：实时内容审计流中断, content_len={}, courseId={}",
                    content != null ? content.length() : 0, courseId, e);
            // 降级策略：审计失败时选择放行，保证业务可用性 (Fail-open)
            result.put("hasBlockedWord", false);
            result.put("blockedWords", List.of());
        }

        return result;
    }

    /**
     * 将屏蔽词实体转换为视图对象
     * 用于对外输出，隔离持久层字段。
     *
     * @param blockedWord 屏蔽词实体
     * @return 屏蔽词视图对象
     */
    public BlockedWordVO convertToVO(BlockedWord blockedWord) {
        if (blockedWord == null) {
            return null;
        }
        BlockedWordVO vo = new BlockedWordVO();
        BeanUtils.copyProperties(blockedWord, vo);
        return vo;
    }

    /**
     * 批量转换屏蔽词实体列表
     *
     * @param blockedWords 屏蔽词实体列表
     * @return 屏蔽词视图对象列表
     */
    public List<BlockedWordVO> convertToVOList(List<BlockedWord> blockedWords) {
        if (blockedWords == null || blockedWords.isEmpty()) {
            return List.of();
        }
        return blockedWords.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
}
