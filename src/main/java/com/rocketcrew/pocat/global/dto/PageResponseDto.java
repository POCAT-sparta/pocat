package com.rocketcrew.pocat.global.dto;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

/* 공통 응답 WrapperDTO
 Page객체를 그대로 노출하지 않고, 필요한 부분만 공통응답 포맷으로 변환해주는 DTO*/
@Builder
public record PageResponseDto<T> (
        // PageResponseDto<T> -> <T>를 사용하여 다양한 종류의 리스트를 담을 수 있게

        // 실제 데이터 리스트
        List<T> content,

        // 현재 페이지 (컴퓨터는 0부터 시작하기 때문에 리턴해줄 때 +1 해주기)
        int pageNumber,

        // 한 페이지에 몇개씩 보여줄지
        int size,

        // 전체 데이터 개수 (ex 검색결과 총 50건)
        long totalElements,

        // 전체 페이지 수 (ex 50건을 10개씩 보여주면 총 5페이지)
        int totalPages
) {
    public static <T, P> PageResponseDto<T> of(Page<P> page, List<T> content) {
        return PageResponseDto.<T>builder()
                .content(content)
                .pageNumber(page.getNumber() + 1) // 0 페이지 부터 시작을 +1 해주므로 1페이지 부터 시작
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
/* <P> -> PageType : JPA가 넘겨준 엔티티 (Client, Project) 페이지 객체, 페이징 계산을 위해서 필요
   <T> -> TargetType : 클라이언트에게 보낼 변환된 DTO
   분리한 이유 : 보통 DB에서 가져온 건 엔티티(P)지만, 우리가 응답할 건 DTO(T)이기 때문,
   두 타입이 서로 다른 것을 컴파일러에게 알려주어 타입 안정성을 확보한 것*/
