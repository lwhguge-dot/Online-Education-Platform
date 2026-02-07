package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知表 Mapper 接口
 *
 * @author Antigravity
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
