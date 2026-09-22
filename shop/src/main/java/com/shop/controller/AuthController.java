package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.LoginDTO;
import com.shop.dto.RegisterDTO;
import com.shop.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")          // ★ 写 /auth，不要写 /shop/auth
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        return authService.login(dto.getUsername(), dto.getPassword(), request);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterDTO dto) {
        return authService.register(dto.getShopName(), dto.getUsername(), dto.getPassword());
    }

    /** 退出登录：令牌从 Redis 删除，立刻失效。本接口会被拦截器拦（必须带合法令牌才能退出） */
    @PostMapping("/logout")
    public Result<String> logout() {
        return authService.logout();
    }
}
