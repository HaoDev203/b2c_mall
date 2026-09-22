package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.MallLoginDTO;
import com.shop.dto.MallRegisterDTO;
import com.shop.service.MallAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 前台买家认证接口。
 * ★ 路径前缀 /mall —— 和后台 /auth 分开，且会在 WebMvcConfig 里被白名单放行。
 * ★ 写 /mall/auth，不要写 /shop/mall/auth —— 网关的 StripPrefix=1 会剥掉 /shop。
 */
@RestController
@RequestMapping("/mall/auth")
@RequiredArgsConstructor
public class MallAuthController {

    private final MallAuthService mallAuthService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody MallRegisterDTO dto) {
        return mallAuthService.register(dto);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody MallLoginDTO dto, HttpServletRequest request) {
        return mallAuthService.login(dto, request);
    }
}
