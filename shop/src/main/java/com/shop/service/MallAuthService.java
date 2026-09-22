package com.shop.service;

import com.shop.account.UserType;
import com.shop.common.Result;
import com.shop.dto.LoginUser;
import com.shop.dto.MallLoginDTO;
import com.shop.dto.MallRegisterDTO;
import com.shop.entity.User;
import com.shop.event.UserLoginEvent;
import com.shop.event.UserRegisteredEvent;
import com.shop.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 前台买家注册 / 登录。
 *
 * ★★ 本类是本阶段最重要的"证据"：
 *    它注入了 publisher，但**没有注入 LoginLogMapper、MessageMapper 之外的任何东西**。
 *    登录日志、登录次数、账号初始化，全部由观察者完成 —— 和 AuthService（后台）走的是同一批观察者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallAuthService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_AVATAR = "/avatars/default.png";

    /** ★ 和 AuthService 一样用 static final，别每次登录都 new 一个（BCrypt 初始化很贵） */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    /** ★ 只注入事件发布器 —— 观察者会接手剩下的事 */
    private final ApplicationEventPublisher publisher;

    private final TokenService tokenService;

    // ==================== 注册 ====================
    public Result<String> register(MallRegisterDTO dto) {
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            return Result.fail("账号和密码不能为空");
        }
        if (dto.getPassword().length() < 6) {
            return Result.fail("密码至少 6 位");
        }
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
            return Result.fail("账号已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setAvatarUrl(DEFAULT_AVATAR);
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname()
                : "用户" + dto.getUsername());              // 没填昵称就给个默认的
        user.setCreatedAt(LocalDateTime.now().format(FMT));
        userMapper.insert(user);                            // id 自动回填到 user.getId()

        // ★★ 核心：和后台注册发的是**同一个事件类型**，只是 userType 不同。
        //    账号初始化观察者会自动接手，这里一行初始化代码都不用写。
        publisher.publishEvent(new UserRegisteredEvent(
                user.getId(),
                null,                                       // 买家不属于任何店铺
                user.getUsername(),
                UserType.BUYER));

        log.info("【MallAuthService】买家「{}」注册成功，已发布 UserRegisteredEvent", user.getUsername());
        return Result.ok("注册成功");
    }

    // ==================== 登录 ====================
    public Result<String> login(MallLoginDTO dto, HttpServletRequest request) {
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            return Result.fail("账号或密码不能为空");
        }

        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            return Result.fail("账号不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.fail("账号已被禁用");
        }
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            return Result.fail("密码错误");
        }

        // ★★ 核心：发布事件后本方法即结束。
        //    写 tb_login_logs（审计日志观察者）、累加 login_count（用户状态观察者）都由观察者完成 ——
        //    这正是需求 Step 5.3 要验证的"观察者复用了"。
        publisher.publishEvent(new UserLoginEvent(
                user.getId(),
                null,                                       // 买家没有店铺
                user.getUsername(),
                UserType.BUYER,                             // ★ 关键：告诉观察者这次是买家
                resolveIp(request),
                request.getHeader("User-Agent"),
                LocalDateTime.now().format(FMT)));

        // 令牌签发属于主流程（必须同步返回），所以不交给观察者 —— 和后台逻辑一致
        String token = tokenService.create(new LoginUser(
                user.getId(), null, user.getUsername(), user.getAvatarUrl()));

        log.info("【MallAuthService】买家 {} 登录成功，已发布 UserLoginEvent 并签发令牌", user.getUsername());
        return Result.ok(token);
    }

    // ==================== 小工具 ====================
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
