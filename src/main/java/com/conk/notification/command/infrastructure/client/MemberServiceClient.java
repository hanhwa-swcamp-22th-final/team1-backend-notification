package com.conk.notification.command.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * member-service HTTP 클라이언트
 *
 * ASN_CREATED, ORDER_REGISTERED 이벤트 수신 시,
 * 알림을 보낼 계정 목록을 member-service REST API로 조회하는 역할을 한다.
 *
 * RestTemplate: Spring에서 제공하는 동기(Synchronous) HTTP 클라이언트.
 * HTTP GET/POST 등을 간단하게 호출할 수 있다.
 *
 * ※ 주의: member-service에 아래 엔드포인트가 구현되어 있어야 한다.
 *   - GET /member/accounts/by-tenant?tenantId={tenantId}&roleId={roleId}
 *   - GET /member/accounts/by-seller?sellerId={sellerId}&roleId={roleId}
 *   응답 형식: List<MemberAccountInfo>
 */
@Component
public class MemberServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceClient.class);

    // application.properties의 member.service.url 값을 주입받는다
    @Value("${member.service.url}")
    private String memberServiceUrl;

    // RestTemplate: HTTP 요청을 보내는 Spring 기본 클라이언트
    // @Bean으로 등록하거나 new로 생성 가능. 여기서는 간단하게 필드로 생성.
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 특정 테넌트의 특정 역할 계정 목록을 조회한다.
     *
     * 사용 사례: ASN_CREATED 이벤트 → tenantId의 WH_MANAGER 전원에게 알림
     *
     * @param tenantId 테넌트 ID
     * @param roleId   역할 ID (예: "ROLE_WH_MANAGER")
     * @return 해당 조건에 맞는 계정 정보 목록. member-service 호출 실패 시 빈 리스트 반환.
     */
    public List<MemberAccountInfo> getAccountsByTenantAndRole(String tenantId, String roleId) {
        // URL 조합: http://localhost:8081/member/accounts/by-tenant?tenantId=xxx&roleId=yyy
        String url = memberServiceUrl + "/member/accounts/by-tenant?tenantId=" + tenantId + "&roleId=" + roleId;

        log.info("[MemberServiceClient] 테넌트 계정 조회 요청: tenantId={}, roleId={}", tenantId, roleId);

        try {
            // exchange(): HTTP 요청을 보내고 응답을 지정한 타입으로 받는다.
            // ParameterizedTypeReference: List<MemberAccountInfo> 같은 제네릭 타입을 런타임에 유지하기 위해 사용
            ResponseEntity<List<MemberAccountInfo>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null, // 요청 body 없음 (GET 요청)
                    new ParameterizedTypeReference<List<MemberAccountInfo>>() {}
            );

            List<MemberAccountInfo> accounts = response.getBody();
            log.info("[MemberServiceClient] 조회 결과: {}명", accounts != null ? accounts.size() : 0);
            return accounts != null ? accounts : Collections.emptyList();

        } catch (RestClientException e) {
            // member-service가 내려가 있거나 응답이 없는 경우 예외 발생
            // 알림 누락을 최소화하기 위해 예외를 던지지 않고 빈 리스트를 반환한다.
            // (향후 재시도 로직이나 DLQ(Dead Letter Queue)로 처리 가능)
            log.error("[MemberServiceClient] 테넌트 계정 조회 실패: tenantId={}, 오류={}", tenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 특정 셀러의 특정 역할 계정 목록을 조회한다.
     *
     * 사용 사례: ORDER_REGISTERED 이벤트 → sellerId의 MASTER_ADMIN에게 알림
     *
     * @param sellerId 셀러 ID
     * @param roleId   역할 ID (예: "ROLE_MASTER_ADMIN")
     * @return 해당 조건에 맞는 계정 정보 목록. member-service 호출 실패 시 빈 리스트 반환.
     */
    public List<MemberAccountInfo> getAccountsBySellerAndRole(String sellerId, String roleId) {
        String url = memberServiceUrl + "/member/accounts/by-seller?sellerId=" + sellerId + "&roleId=" + roleId;

        log.info("[MemberServiceClient] 셀러 계정 조회 요청: sellerId={}, roleId={}", sellerId, roleId);

        try {
            ResponseEntity<List<MemberAccountInfo>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MemberAccountInfo>>() {}
            );

            List<MemberAccountInfo> accounts = response.getBody();
            log.info("[MemberServiceClient] 조회 결과: {}명", accounts != null ? accounts.size() : 0);
            return accounts != null ? accounts : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("[MemberServiceClient] 셀러 계정 조회 실패: sellerId={}, 오류={}", sellerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * member-service가 반환하는 계정 정보 DTO
     *
     * member-service의 응답 JSON 구조와 일치해야 한다.
     * 현재 notification-service에서 필요한 최소 필드만 정의한다.
     *
     * 예상 JSON:
     * { "accountId": "1001", "roleId": "ROLE_WH_MANAGER" }
     */
    public static class MemberAccountInfo {

        /** member-service Account.accountId (문자열로 전달받음) */
        private String accountId;

        /** member-service Role.roleId */
        private String roleId;

        public MemberAccountInfo() {}

        public String getAccountId() { return accountId; }
        public String getRoleId() { return roleId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
    }
}
