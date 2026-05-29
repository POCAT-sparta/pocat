package com.rocketcrew.pocat.global.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class HttpRequestUtilsTest {

    @Test
    @DisplayName("XFF 단일 IP → 그대로 반환")
    void resolveClientIp_singleIp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("1.2.3.4");

        assertThat(HttpRequestUtils.resolveClientIp(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("XFF 다중 IP → 마지막 IP 반환")
    void resolveClientIp_multipleIps_returnsLast() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("1.1.1.1, 2.2.2.2, 3.3.3.3");

        assertThat(HttpRequestUtils.resolveClientIp(request)).isEqualTo("3.3.3.3");
    }

    @Test
    @DisplayName("XFF 헤더 없음 → remoteAddr 반환")
    void resolveClientIp_noHeader_returnsRemoteAddr() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("5.5.5.5");

        assertThat(HttpRequestUtils.resolveClientIp(request)).isEqualTo("5.5.5.5");
    }

    @Test
    @DisplayName("XFF = 0:0:0:0:0:0:0:1 → 127.0.0.1")
    void resolveClientIp_ipv6Loopback() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("0:0:0:0:0:0:0:1");

        assertThat(HttpRequestUtils.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("XFF = ::1 → 127.0.0.1")
    void resolveClientIp_ipv6ShortLoopback() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        given(request.getHeader("X-Forwarded-For")).willReturn("::1");

        assertThat(HttpRequestUtils.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }
}
