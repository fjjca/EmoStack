package org.example.aispingboot.util;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aispingboot.DTO.response.UserLoginResponseDTO;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.config.SecurityConfig;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthticationFilter extends OncePerRequestFilter {
    @Resource
    private UserService userService;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // 检查是否为公开路径
        return SecurityConfig.isPublicPATH(requestUri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // 获取请求的URI和方法
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        System.out.println(requestUri);
        System.out.println(method);
        // 1. 提取 JWT token
        String token = JwtTokenUtil.extractTokenFromRequest(request);
        if (StringUtils.hasText(token)) {
            // 2. 验证token并获取用户信息
            JwtTokenUtil.TokenVerificationResult validationResult = JwtTokenUtil.validateToken(token);
            if (validationResult != null && validationResult.isValid()) {
                // 3. 查询用户信息验证用户的状态
                UserLoginResponseDTO.UserDetailResponseDTO user = userService.getUserById(validationResult.getUserId());
                System.out.println(JSONUtil.parseObj(user));
                if (user != null && UserStatus.NORMAL.getCode().equals(user.getStatus())) {
                    // 4. 创建Spring Security认证对象
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + validationResult.getRoleType())
                    );

                    // 创建UsernamePasswordAuthenticationToken对象
                     UsernamePasswordAuthenticationToken authcation = new UsernamePasswordAuthenticationToken(
                            validationResult.getUsername(), // 用户名作为主体
                            null,
                            authorities
                    );

                     // 设置认证信息到Spring Securtity上下文
                    SecurityContextHolder.getContext().setAuthentication(authcation);

                    // 将token存储到请求属性中
                    request.setAttribute("jwtToken", token);
                } else {
                    clearSecurityContext();
                    ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
                }
            } else {
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            }
        } else {
            // 清理上下文
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
            return;
        }
        // 继续过滤器链
        chain.doFilter(request, response);
    }

    // 清理Spring Security上下文
    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
