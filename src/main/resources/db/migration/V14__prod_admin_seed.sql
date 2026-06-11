-- V14: 운영 환경 관리자 계정 초기 생성
--
-- 배포 후 즉시 비밀번호를 변경할 것.
-- 비밀번호 재생성: BCryptPasswordEncoder(10).encode("새비밀번호")
--
-- ON DUPLICATE KEY UPDATE: soft-delete 상태로 이미 존재할 경우 계정을 복원하고 ADMIN 권한을 보장한다.

INSERT INTO users (
    email,
    password,
    nickname,
    user_role,
    is_bid_blocked,
    unpaid_strike,
    created_at,
    updated_at
)
VALUES (
    'admin@pocat.com',
    '$2a$10$ugY8oSSQExzh1kqJaeFQd.tLYUawyhlsDqJ/I1kQ1pL2I9F9D.mJu',
    'admin',
    'ADMIN',
    0,
    0,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    deleted_at = NULL,
    user_role  = 'ADMIN',
    updated_at = NOW();
