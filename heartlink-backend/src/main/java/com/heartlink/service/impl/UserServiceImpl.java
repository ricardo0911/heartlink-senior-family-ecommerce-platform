package com.heartlink.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.config.WeChatMiniProgramProperties;
import com.heartlink.dto.LoginDTO;
import com.heartlink.dto.RegisterDTO;
import com.heartlink.dto.ResetPasswordDTO;
import com.heartlink.dto.UpdateCurrentUserDTO;
import com.heartlink.entity.User;
import com.heartlink.mapper.UserMapper;
import com.heartlink.service.UserService;
import com.heartlink.service.WeChatMiniProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final List<String> CHILD_FALLBACK_NAMES = List.of(
            "张晨曦", "李雨桐", "王子涵", "陈思远", "赵嘉宁", "周明轩",
            "孙雅琪", "吴浩然", "郑欣怡", "冯若彤", "蒋俊凯", "许嘉禾"
    );

    private static final List<String> PARENT_FALLBACK_NAMES = List.of(
            "刘秀兰", "王桂芳", "陈美玲", "黄玉兰", "周美珍", "林秀云",
            "谢桂英", "何淑芬", "张玉梅", "李兰英", "赵春华", "杨素珍"
    );
    private static final int DEFAULT_CREDIT_SCORE = 100;

    private final WeChatMiniProgramService weChatMiniProgramService;
    private final WeChatMiniProgramProperties weChatMiniProgramProperties;

    @Override
    @Transactional
    public User register(RegisterDTO dto) {
        User existing = getByPhone(dto.getPhone());
        if (existing != null) {
            throw new RuntimeException("该手机号已注册");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(DigestUtil.md5Hex(dto.getPassword()));
        user.setNickname(resolveNickname(dto.getNickname(), dto.getRole(), dto.getPhone()));
        user.setRole(dto.getRole());
        user.setStatus(1);
        user.setCreditScore(DEFAULT_CREDIT_SCORE);

        save(user);
        return user;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        User user = getByPhone(dto.getPhone());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!String.valueOf(dto.getRole()).equals(user.getRole())) {
            throw new RuntimeException("身份不匹配，请确认当前账号类型");
        }
        user.setPassword(DigestUtil.md5Hex(dto.getPassword()));
        updateById(user);
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User user = getByPhone(dto.getPhone());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!DigestUtil.md5Hex(dto.getPassword()).equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        if (!dto.getRole().equals(user.getRole())) {
            throw new RuntimeException("身份不匹配，请选择正确的登录身份");
        }

        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        StpUtil.login(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", user);
        return result;
    }

    @Override
    public User getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .eq(User::getDeleted, 0));
    }

    @Override
    public Map<String, Object> wxLogin(String code, String role, String mockOpenid) {
        String openid = resolveOpenid(code, role, mockOpenid);

        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid)
                .eq(User::getRole, role));

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setRole(role);
            user.setNickname(resolveNickname(null, role, openid));
            user.setStatus(1);
            user.setCreditScore(DEFAULT_CREDIT_SCORE);
            save(user);
        }

        StpUtil.login(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", user);
        return result;
    }

    @Override
    @Transactional
    public User bindCurrentUserWechat(String code, String mockOpenid) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("user not found");
        }

        user.setOpenid(resolveOpenid(code, user.getRole(), mockOpenid));
        updateById(user);
        return user;
    }

    private String resolveOpenid(String code, String role, String mockOpenid) {
        String roleText = String.valueOf(role == null ? "" : role).trim().toUpperCase();
        String mock = String.valueOf(mockOpenid == null ? "" : mockOpenid).trim();
        String safeCode = String.valueOf(code == null ? "" : code).trim();

        if (weChatMiniProgramProperties.isEnabled() && !safeCode.isEmpty()) {
            return weChatMiniProgramService.resolveOpenid(safeCode);
        }

        if (!mock.isEmpty()) {
            String safeMock = mock.replaceAll("[^a-zA-Z0-9_\\-:.]", "");
            if (safeMock.isEmpty()) {
                safeMock = DigestUtil.md5Hex(mock);
            }
            return limitLength("mock_" + roleText + "_" + safeMock, 100);
        }

        if (safeCode.isEmpty()) {
            safeCode = RandomUtil.randomString(24);
        }
        return limitLength("wx_" + safeCode, 100);
    }

    private String resolveNickname(String nickname, String role, String seed) {
        String normalized = String.valueOf(nickname == null ? "" : nickname).trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return buildDefaultNickname(role, seed);
    }

    private String buildDefaultNickname(String role, String seed) {
        List<String> candidates = "PARENT".equalsIgnoreCase(role) ? PARENT_FALLBACK_NAMES : CHILD_FALLBACK_NAMES;
        if (candidates.isEmpty()) {
            return "PARENT".equalsIgnoreCase(role) ? "长辈用户" : "子女用户";
        }
        int index = Math.floorMod(String.valueOf(seed == null ? role : seed).hashCode(), candidates.size());
        return candidates.get(index);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public User getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        return getById(userId);
    }

    @Override
    @Transactional
    public User updateCurrentUserProfile(UpdateCurrentUserDTO dto) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("user not found");
        }
        if (dto == null) {
            return user;
        }

        String nickname = String.valueOf(dto.getNickname() == null ? "" : dto.getNickname()).trim();
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }

        if (dto.getAvatar() != null) {
            String avatar = dto.getAvatar().trim();
            if (StringUtils.hasText(avatar) && !isAllowedAvatar(avatar)) {
                throw new RuntimeException("头像地址格式不正确");
            }
            user.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
        }

        updateById(user);
        return getCurrentUser();
    }

    private boolean isAllowedAvatar(String avatar) {
        return avatar.startsWith("/upload/")
                || avatar.startsWith("http://")
                || avatar.startsWith("https://")
                || avatar.startsWith("data:image/");
    }
}
