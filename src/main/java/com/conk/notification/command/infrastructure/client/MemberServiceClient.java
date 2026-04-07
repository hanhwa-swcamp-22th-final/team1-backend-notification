package com.conk.notification.command.infrastructure.client;

import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.common.exception.RetryableKafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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

    private final RestTemplate restTemplate;

    public MemberServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 특정 테넌트의 특정 역할 계정 목록을 조회한다.
     *
     * 사용 사례: ASN_CREATED 이벤트 → tenantId의 WH_MANAGER 전원에게 알림
     *
     * @param tenantId 테넌트 ID
     * @param roleId   역할 ID (예: "ROLE_WH_MANAGER")
     * @return 해당 조건에 맞는 계정 정보 목록. 정상 응답이지만 결과가 없으면 빈 리스트를 반환한다.
     */
    public List<MemberAccountInfo> getAccountsByTenantAndRole(String tenantId, String roleId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(memberServiceUrl)
                .path("/member/accounts/by-tenant")
                .queryParam("tenantId", tenantId)
                .queryParam("roleId", roleId)
                .build(true)
                .toUri();

        log.info("[MemberServiceClient] 테넌트 계정 조회 요청: tenantId={}, roleId={}", tenantId, roleId);

        try {
            ResponseEntity<List<MemberAccountInfo>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MemberAccountInfo>>() {}
            );

            List<MemberAccountInfo> accounts = response.getBody();
            log.info("[MemberServiceClient] 조회 결과: {}명", accounts != null ? accounts.size() : 0);
            return accounts != null ? accounts : Collections.emptyList();

        } catch (RestClientException e) {
            // 외부 연동 장애는 "수신자 없음"과 구분해야 하므로 retryable 예외로 전환한다.
            throw new RetryableKafkaException(
                    ErrorCode.RECIPIENT_LOOKUP_FAILED,
                    "member-service 수신자 조회 실패 tenantId=%s, roleId=%s".formatted(tenantId, roleId)
            );
        }
    }

    /**
     * 특정 셀러의 특정 역할 계정 목록을 조회한다.
     *
     * 사용 사례: ORDER_REGISTERED 이벤트 → sellerId의 MASTER_ADMIN에게 알림
     *
     * @param sellerId 셀러 ID
     * @param roleId   역할 ID (예: "ROLE_MASTER_ADMIN")
     * @return 해당 조건에 맞는 계정 정보 목록. 정상 응답이지만 결과가 없으면 빈 리스트를 반환한다.
     */
    public List<MemberAccountInfo> getAccountsBySellerAndRole(String sellerId, String roleId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(memberServiceUrl)
                .path("/member/accounts/by-seller")
                .queryParam("sellerId", sellerId)
                .queryParam("roleId", roleId)
                .build(true)
                .toUri();

        log.info("[MemberServiceClient] 셀러 계정 조회 요청: sellerId={}, roleId={}", sellerId, roleId);

        try {
            ResponseEntity<List<MemberAccountInfo>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MemberAccountInfo>>() {}
            );

            List<MemberAccountInfo> accounts = response.getBody();
            log.info("[MemberServiceClient] 조회 결과: {}명", accounts != null ? accounts.size() : 0);
            return accounts != null ? accounts : Collections.emptyList();

        } catch (RestClientException e) {
            // 외부 연동 장애는 "수신자 없음"과 구분해야 하므로 retryable 예외로 전환한다.
            throw new RetryableKafkaException(
                    ErrorCode.RECIPIENT_LOOKUP_FAILED,
                    "member-service 수신자 조회 실패 sellerId=%s, roleId=%s".formatted(sellerId, roleId)
            );
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
