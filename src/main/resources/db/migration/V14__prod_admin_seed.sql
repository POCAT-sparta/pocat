-- V14: 운영 환경 관리자 계정 초기 생성
--
-- 임시 비밀번호: Admin1234!
-- 배포 후 즉시 비밀번호를 변경할 것.
-- 비밀번호 재생성: BCryptPasswordEncoder(10).encode("새비밀번호")
--
-- INSERT IGNORE: email unique 제약으로 이미 존재하면 스킵 (멱등)

INSERT IGNORE INTO users (
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
);
