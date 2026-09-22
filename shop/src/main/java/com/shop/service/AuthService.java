package com.shop.service;

import com.shop.account.UserType;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.dto.LoginUser;
import com.shop.entity.Employee;
import com.shop.entity.Shop;
import com.shop.event.UserLoginEvent;
import com.shop.event.UserRegisteredEvent;
import com.shop.mapper.EmployeeMapper;
import com.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_AVATAR = "/avatars/default.png";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final EmployeeMapper employeeMapper;
    private final ShopMapper shopMapper;

    /** ★ 只注入事件发布器 —— 注意：这里没有 LoginLogMapper，也没有 MessageMapper */
    private final ApplicationEventPublisher publisher;

    /** ★ 令牌的签发与销毁属于主流程（必须同步返回 / 立即生效），所以放在这里，不交给观察者 */
    private final TokenService tokenService;

    // ==================== 登录 ====================
    public Result<String> login(String username, String password, HttpServletRequest request) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.fail("账号或密码不能为空");
        }

        Employee employee = employeeMapper.selectByUsername(username);
        if (employee == null) {
            return Result.fail("账号不存在");
        }
        if (employee.getStatus() != null && employee.getStatus() == 0) {
            return Result.fail("账号已被禁用");
        }
        if (!PASSWORD_ENCODER.matches(password, employee.getPassword())) {
            return Result.fail("密码错误");
        }

        // ★★ 核心：发布事件后本方法即结束。
        //    记录日志、更新登录次数这两件事，由两个观察者各自完成。
        publisher.publishEvent(new UserLoginEvent(
                employee.getId(),
                employee.getShopId(),
                employee.getUsername(),
                UserType.ADMIN,                 // ★ 新增：后台员工
                resolveIp(request),
                request.getHeader("User-Agent"),
                LocalDateTime.now().format(FMT)
        ));

        // ★ 真正的令牌：存进 Redis（30 分钟有效期），后续请求由 AuthInterceptor 校验
        String token = tokenService.create(new LoginUser(
                employee.getId(), employee.getShopId(), employee.getUsername(), employee.getAvatarUrl()));

        log.info("【AuthService】{} 登录成功，已发布 UserLoginEvent 并签发令牌", username);
        return Result.ok(token);
    }

    // ==================== 退出登录 ====================
    /** 从 Redis 删掉令牌，立刻失效（这是 JWT 做不到的） */
    public Result<String> logout() {
        tokenService.remove(UserContext.getToken());
        log.info("【AuthService】{} 已退出登录", UserContext.getUser() == null ? "?" : UserContext.getUser().getUsername());
        return Result.ok("已退出登录");
    }

    // ==================== 注册 ====================
    public Result<String> register(String shopName, String username, String password) {
        if (!StringUtils.hasText(shopName) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.fail("店铺名、账号、密码都不能为空");
        }
        if (shopMapper.selectByName(shopName) != null) {
            return Result.fail("店铺名已存在");
        }
        if (employeeMapper.selectByUsername(username) != null) {
            return Result.fail("账号已存在");
        }

        String encoded = PASSWORD_ENCODER.encode(password);

        // 1. 建店铺（id 会回填到 shop.getId()）
        Shop shop = new Shop();
        shop.setShopName(shopName);
        shop.setAdminAccount(username);
        shop.setAdminPassword(encoded);
        shop.setLogoUrl(DEFAULT_AVATAR);
        shopMapper.insert(shop);

        // 2. 建管理员员工（= 后台登录账号，id 会回填）
        Employee admin = new Employee();
        admin.setShopId(shop.getId());
        admin.setUsername(username);
        admin.setPassword(encoded);
        admin.setAvatarUrl(DEFAULT_AVATAR);
        employeeMapper.insert(admin);

        // ★★ 核心：账号初始化、发欢迎通知，全部交给观察者
        publisher.publishEvent(new UserRegisteredEvent(admin.getId(), shop.getId(), username, UserType.ADMIN));

        log.info("【AuthService】店铺「{}」注册成功，已发布 UserRegisteredEvent", shopName);
        return Result.ok("注册成功");
    }

    // ==================== 小工具 ====================
    /** 取真实客户端 IP：走网关时真实 IP 在 X-Forwarded-For 里 */
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
