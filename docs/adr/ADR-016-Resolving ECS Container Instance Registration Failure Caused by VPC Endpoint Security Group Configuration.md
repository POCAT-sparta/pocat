# ADR-00X: ECS EC2 Capacity Provider 등록 실패 원인 분석 및 VPC Endpoint 보안그룹 표준화

| 항목      | 내용         |
| ------- |------------|
| **날짜**  | 2026-06-08 |
| **상태**  | Accepted   |
| **결정자** | 배포팀 전체     |

---

## 맥락 (Context)

POCAT 프로젝트는 ECS EC2 기반으로 애플리케이션 서버와 배치 서버를 운영한다.

기존 앱 서버는 ECS 클러스터 생성 시 자동 생성된 Auto Scaling Group과 Launch Template을 사용하여 정상적으로 ECS Cluster에 등록되고 있었다.

배치 서버를 추가하기 위해 다음 작업을 수행하였다.

* ECS Capacity Provider용 Auto Scaling Group 생성
* ECS Launch Template 생성
* Amazon Linux 2023 AMI 사용
* ECS Agent 설치 및 Cluster 등록용 User Data 추가
* ECS EC2 인스턴스 생성

배치용 EC2 인스턴스는 정상적으로 생성되었으나 ECS Cluster에는 등록되지 않는 문제가 발생하였다.

---

## 문제 현상 (Problem)

생성된 EC2에서 다음 현상이 확인되었다.

### ECS Agent 상태

```bash
systemctl status ecs
```

초기 상태:

```text
inactive (dead)
```

ECS Agent가 실행되지 않음.

---

### ECS Agent 수동 설치 후

```bash
sudo dnf install -y ecs-init
sudo systemctl enable ecs
sudo systemctl start ecs
```

이후 ECS Agent 컨테이너는 실행되었으나 ECS Cluster 등록은 여전히 실패하였다.

---

### ECS Endpoint 연결 실패

```bash
curl -I https://ecs.ap-northeast-2.amazonaws.com
```

결과:

```text
Connection timed out
```

반면

```bash
curl -I https://www.google.com
```

은 정상 응답하였다.

즉,

* NAT Gateway 경로는 정상
* 인터넷 연결 정상
* ECS Endpoint 접근만 실패

상태였다.

---

## 원인 분석 (Root Cause)

### 1. ECS Agent는 AWS ECS API와 지속적으로 통신한다

ECS Agent는 다음 작업을 수행하기 위해 ECS API에 접근한다.

* Cluster 등록
* Task 배치 요청 수신
* 상태 보고
* Heartbeat 전송

즉,

```text
EC2
 ↓
ECS Agent
 ↓
ECS API
```

통신이 반드시 가능해야 한다.

---

### 2. VPC Interface Endpoint 사용 중

프로젝트는 비용 절감 및 Private Network 구성을 위해 다음 Interface Endpoint를 사용하고 있었다.

| Endpoint        |
| --------------- |
| ECS             |
| ECS Agent       |
| ECS Telemetry   |
| ECR API         |
| ECR DKR         |
| CloudWatch Logs |

Private DNS 활성화 상태였기 때문에

```text
ecs.ap-northeast-2.amazonaws.com
```

요청은 실제 인터넷이 아니라

```text
VPC Endpoint ENI
```

로 라우팅되고 있었다.

---

### 3. Endpoint 보안그룹 문제

Interface Endpoint에는 다음 보안그룹이 연결되어 있었다.

```text
pocat-app-sg
```

해당 보안그룹 인바운드 규칙:

```text
HTTPS 443
Source = pocat-app-sg
```

즉

```text
앱 서버
  ↓
Endpoint
```

만 허용된 상태였다.

배치 서버는

```text
pocat-batch-sg
```

를 사용하고 있었으므로

```text
배치 서버
 ↓
Endpoint
```

트래픽이 Security Group 수준에서 차단되고 있었다.

---

## 결정 (Decision)

### 1. Endpoint 접근용 전용 Security Group 생성

다음 보안그룹을 생성한다.

```text
pocat-vpce-sg
```

---

### 2. 모든 Interface Endpoint에 동일 Security Group 적용

적용 대상:

| Endpoint        |
| --------------- |
| ECS             |
| ECS Agent       |
| ECS Telemetry   |
| ECR API         |
| ECR DKR         |
| CloudWatch Logs |

기존:

```text
Endpoint
 └ pocat-app-sg
```

변경:

```text
Endpoint
 └ pocat-vpce-sg
```

---

### 3. 접근 허용 서버를 Source로 등록

```text
HTTPS 443
Source = pocat-app-sg

HTTPS 443
Source = pocat-batch-sg

HTTPS 443
Source = pocat-monitoring-sg
```

필요 시 추가:

```text
pocat-kafka-sg
pocat-es-sg
pocat-redis-sg
```

---

### 4. ECS Cluster는 단일 Cluster 유지

별도 Batch Cluster를 생성하지 않는다.

배치 EC2는 다음 Instance Attribute를 사용한다.

```bash
ECS_INSTANCE_ATTRIBUTES={"service":"batch"}
```

이를 통해 Capacity Provider 및 Placement Constraint에서 배치 서버를 구분한다.

---

## 결과 (Consequences)

### 긍정적 영향

* ECS App / Batch 서버 모두 동일 Cluster 사용 가능
* VPC Endpoint 보안 정책 일원화
* Endpoint 추가 시 SG 관리 복잡도 감소
* 신규 ECS 인스턴스 등록 문제 예방
* ECR Pull, CloudWatch Logs 등 AWS 서비스 접근 정책 일관성 확보

---

### 부정적 영향 / 주의사항

* 새로운 서버군 추가 시 `pocat-vpce-sg` 인바운드 규칙 업데이트 필요
* Interface Endpoint는 Security Group 설정 오류 시 원인 파악이 어려움
* ECS Agent 장애 발생 시 Endpoint 접근 여부를 우선 확인해야 함

---

## 트러블슈팅 과정

### ECS Agent 상태 확인

```bash
systemctl status ecs
```

### ECS Agent 로그 확인

```bash
sudo docker logs ecs-agent
```

### ECS Metadata 확인

```bash
curl http://localhost:51678/v1/metadata
```

### ECS Endpoint 연결 확인

```bash
curl -I https://ecs.ap-northeast-2.amazonaws.com
```

### ECR Endpoint 연결 확인

```bash
curl -I https://api.ecr.ap-northeast-2.amazonaws.com
```

---

## 최종 확인

배치 서버에서 다음 응답 확인.

```bash
curl http://localhost:51678/v1/metadata
```

결과:

```json
{
  "Cluster":"pocat-app-cluster",
  "ContainerInstanceArn":"arn:aws:ecs:..."
}
```

`ContainerInstanceArn`이 반환되면 ECS Cluster 등록 성공으로 판단한다.

---

## 관련 문서

* ECS Capacity Provider 설정 문서
* ECS Launch Template 설정 문서
* VPC Endpoint 구성 문서
* AWS ECS EC2 Launch Type 운영 정책
