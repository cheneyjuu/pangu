package com.pangu.interfaces.security;

import com.pangu.domain.model.user.DataScopeType;
import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import com.pangu.infrastructure.persistence.mapper.UserMapper;
import com.pangu.infrastructure.persistence.mapper.UserMapper.SysUserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器，用于解析 Authorization 请求头并构建 Spring Security 上下文
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    Long uid = jwtTokenProvider.getUidFromToken(token);
                    Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                    if (uid != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        // 1. 根据 UID 查验是否有绑定的后台管理账号和对应的数据范围
                        SysUserDto sysUser = userMapper.selectSysUserByUid(uid);
                        UserSecurityContext userCtx;

                        if (sysUser != null) {
                            // 2. 网格员、物业管理员等 B/G 端用户
                            List<Long> buildingIds = Collections.emptyList();
                            if (DataScopeType.CUSTOM_BUILDING.getValue().equals(sysUser.getDataScope())) {
                                buildingIds = userMapper.selectBuildingIdsByUserId(sysUser.getUserId());
                            }
                            userCtx = UserSecurityContext.builder()
                                    .userId(sysUser.getUserId())
                                    .deptId(sysUser.getDeptId())
                                    .dataScope(sysUser.getDataScope())
                                    .authorizedBuildingIds(buildingIds)
                                    .uid(uid)
                                    .tenantId(tenantId)
                                    .build();
                        } else {
                            // 3. 纯 C 端业主，兜底为仅本人数据权限，userId 赋为 uid
                            userCtx = UserSecurityContext.builder()
                                    .userId(uid)
                                    .deptId(null)
                                    .dataScope(DataScopeType.SELF.getValue())
                                    .authorizedBuildingIds(Collections.emptyList())
                                    .uid(uid)
                                    .tenantId(tenantId)
                                    .build();
                        }

                        // 4. 构建 Spring Security 的认证上下文，并在 credentials 处传入 token (供下游审计或微调)
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userCtx,
                                token,
                                Collections.emptyList()
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                // 静默处理，认证失败由 Security 或者是具体的 Controller 拦截判定
            }
        }
        filterChain.doFilter(request, response);
    }
}
