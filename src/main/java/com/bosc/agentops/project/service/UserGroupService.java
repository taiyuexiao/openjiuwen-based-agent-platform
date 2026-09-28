package com.bosc.agentops.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.dto.GroupCreateReq;
import com.bosc.agentops.project.dto.GroupUpdateReq;
import com.bosc.agentops.project.entity.UserGroup;
import com.bosc.agentops.project.entity.UserGroupMember;
import com.bosc.agentops.project.mapper.UserGroupMapper;
import com.bosc.agentops.project.mapper.UserGroupMemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserGroupService {

    private final UserGroupMapper userGroupMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final AuditService auditService;

    public UserGroupService(UserGroupMapper userGroupMapper,
                            UserGroupMemberMapper userGroupMemberMapper,
                            AuditService auditService) {
        this.userGroupMapper = userGroupMapper;
        this.userGroupMemberMapper = userGroupMemberMapper;
        this.auditService = auditService;
    }

    @Transactional
    public UserGroup create(GroupCreateReq req) {
        String userId = RequestContext.currentUserId();
        UserGroup group = new UserGroup();
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        group.setOwnerId(userId);
        group.setCreatedBy(userId);
        try {
            userGroupMapper.insert(group);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "用户组名称已存在: " + req.getName());
        }
        UserGroupMember creator = new UserGroupMember();
        creator.setGroupId(group.getId());
        creator.setUserId(userId);
        userGroupMemberMapper.insert(creator);
        auditService.record("group", "create", "user_group", group.getId(),
                Map.of("name", group.getName()));
        return group;
    }

    @Transactional
    public UserGroup update(GroupUpdateReq req) {
        UserGroup group = getOrThrow(req.getId());
        requireOwner(group);
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        try {
            userGroupMapper.updateById(group);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "用户组名称已存在: " + req.getName());
        }
        auditService.record("group", "update", "user_group", group.getId(),
                Map.of("name", group.getName()));
        return group;
    }

    @Transactional
    public void delete(Long id) {
        UserGroup group = getOrThrow(id);
        requireOwner(group);
        userGroupMemberMapper.delete(
                new LambdaQueryWrapper<UserGroupMember>().eq(UserGroupMember::getGroupId, id));
        userGroupMapper.deleteById(id);
        auditService.record("group", "delete", "user_group", id, Map.of("name", group.getName()));
    }

    @Transactional
    public UserGroupMember addMember(Long groupId, String memberUserId) {
        UserGroup group = getOrThrow(groupId);
        requireOwner(group);
        UserGroupMember member = new UserGroupMember();
        member.setGroupId(groupId);
        member.setUserId(memberUserId);
        try {
            userGroupMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "用户已在组内: " + memberUserId);
        }
        auditService.record("group", "member:add", "user_group", groupId,
                Map.of("userId", memberUserId));
        return member;
    }

    @Transactional
    public void removeMember(Long groupId, String memberUserId) {
        UserGroup group = getOrThrow(groupId);
        requireOwner(group);
        int deleted = userGroupMemberMapper.delete(new LambdaQueryWrapper<UserGroupMember>()
                .eq(UserGroupMember::getGroupId, groupId)
                .eq(UserGroupMember::getUserId, memberUserId));
        if (deleted == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不在组内: " + memberUserId);
        }
        auditService.record("group", "member:remove", "user_group", groupId,
                Map.of("userId", memberUserId));
    }

    public List<UserGroup> list() {
        return userGroupMapper.selectList(null);
    }

    public List<UserGroupMember> listMembers(Long groupId) {
        getOrThrow(groupId);
        return userGroupMemberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMember>().eq(UserGroupMember::getGroupId, groupId));
    }

    public UserGroup getOrThrow(Long id) {
        UserGroup group = userGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户组不存在: " + id);
        }
        return group;
    }

    /** 本期简化：仅组创建者可改/删组、加人/移人 */
    private void requireOwner(UserGroup group) {
        String userId = RequestContext.currentUserId();
        if (!group.getOwnerId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅组创建者可管理该组: " + group.getId());
        }
    }
}
