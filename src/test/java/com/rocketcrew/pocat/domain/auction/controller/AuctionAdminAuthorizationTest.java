package com.rocketcrew.pocat.domain.auction.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuctionController Admin @PreAuthorize 어노테이션 검증")
class AuctionAdminAuthorizationTest {

    @Test
    @DisplayName("Admin 핸들러 3개에 @PreAuthorize(\"hasRole('ADMIN')\") 어노테이션 존재")
    void adminHandlersHavePreAuthorizeAnnotation() {
        Method[] methods = AuctionController.class.getDeclaredMethods();

        long adminMethodsWithPreAuthorize = Arrays.stream(methods)
                .filter(m -> {
                    PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
                    return annotation != null && annotation.value().equals("hasRole('ADMIN')");
                })
                .count();

        assertThat(adminMethodsWithPreAuthorize)
                .as("getAdminAuctions, inspectAuction, adminCancelAuction — 3개 메서드에 @PreAuthorize 적용 확인")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("getAdminAuctions 메서드에 @PreAuthorize(\"hasRole('ADMIN')\") 존재")
    void getAdminAuctions_hasPreAuthorize() throws NoSuchMethodException {
        // AuctionController.getAdminAuctions 메서드 존재 확인 (파라미터 미검증)
        Method[] methods = AuctionController.class.getDeclaredMethods();
        Method target = Arrays.stream(methods)
                .filter(m -> m.getName().equals("getAdminAuctions"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("getAdminAuctions 메서드 없음"));

        PreAuthorize annotation = target.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("inspectAuction 메서드에 @PreAuthorize(\"hasRole('ADMIN')\") 존재")
    void inspectAuction_hasPreAuthorize() {
        Method[] methods = AuctionController.class.getDeclaredMethods();
        Method target = Arrays.stream(methods)
                .filter(m -> m.getName().equals("inspectAuction"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("inspectAuction 메서드 없음"));

        PreAuthorize annotation = target.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("adminCancelAuction 메서드에 @PreAuthorize(\"hasRole('ADMIN')\") 존재")
    void adminCancelAuction_hasPreAuthorize() {
        Method[] methods = AuctionController.class.getDeclaredMethods();
        Method target = Arrays.stream(methods)
                .filter(m -> m.getName().equals("adminCancelAuction"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("adminCancelAuction 메서드 없음"));

        PreAuthorize annotation = target.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }
}
