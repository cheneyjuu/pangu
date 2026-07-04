package com.pangu.interfaces.web.controller.dto;

/**
 * 管理端二级导航菜单。
 */
public record NavPageResponse(
        String id,
        String label,
        Integer order) {
}
