package com.pangu.interfaces.web.controller.dto;

import java.util.List;

/**
 * 管理端一级导航菜单。
 */
public record NavMenuResponse(
        String id,
        String label,
        String icon,
        Integer order,
        List<NavPageResponse> pages) {
}
