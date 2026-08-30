package com.club.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.agent.common.ResultCode;
import com.club.agent.entity.Message;
import com.club.agent.exception.BizException;
import com.club.agent.mapper.MessageMapper;
import com.club.agent.service.MessageService;
import com.club.agent.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 站内消息域实现（从 MessageController 迁入，恢复 Controller 只依赖 Service 的分层规范）。
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    @Override
    public IPage<MessageVO> list(Long userId, long page, long size, Integer readFlag) {
        Page<Message> p = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRecipientId, userId)
                        .eq(readFlag != null, Message::getReadFlag, readFlag)
                        .orderByDesc(Message::getCreatedAt));
        // entity 转 VO：id 字符串化防 JS 精度丢失（标记已读需要精确 id）
        Page<MessageVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getRecipientId, userId)
                .eq(Message::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    @Override
    public void markRead(Long userId, Long id) {
        int updated = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getRecipientId, userId)
                .set(Message::getReadFlag, 1));
        if (updated == 0) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    private MessageVO toVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setRecipientId(m.getRecipientId());
        vo.setType(m.getType());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setRefConceptId(m.getRefConceptId());
        vo.setRefActivityId(m.getRefActivityId());
        vo.setReadFlag(m.getReadFlag());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}
