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
 * ASN_CREATED 이벤트 수신 시, 알림을 보낼 창고 관리자 목록을 member-service REST API로 조회한다.
 *
 * ※ 주의: member-service에 아래 엔드포인트가 구현되어 있어야 한다.
 *   - GET /member/accounts/by-warehouse?warehouseId={warehouseId}&roleId={roleId}
 *   응답 형식: List<MemberAccountInfo>
 */
@Component
public class MemberServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceClient.class);

    @Value("${member.service.url}")
    private String memberServiceUrl;

    private final RestTemplate restTemplate;

    public MemberServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 특정 창고의 WH_MANAGER 계정 목록을 조회한다.
     *
     * 사용 사례: ASN_CREATED 이벤트 → ASN 등록 시 선택한 창고의 WH_MANAGER에게 알림
     *
     * @param warehouseId 창고 ID
     * @param roleId      역할 ID (예: "ROLE_WH_MANAGER")
     * @return 해당 조건에 맞는 계정 정보 목록. 결과가 없으면 빈 리스트를 반환한다.
     */
    public List<MemberAccountInfo> getManagersByWarehouse(String warehouseId, String roleId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(memberServiceUrl)
                .path("/member/accounts/by-warehouse")
                .queryParam("warehouseId", warehouseId)
                .queryParam("roleId", roleId)
                .build(true)
                .toUri();

        log.info("[MemberServiceClient] 창고 관리자 조회 요청: warehouseId={}, roleId={}", warehouseId, roleId);

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
            throw new RetryableKafkaException(
                    ErrorCode.RECIPIENT_LOOKUP_FAILED,
                    "member-service 창고 관리자 조회 실패 warehouseId=%s, roleId=%s".formatted(warehouseId, roleId)
            );
        }
    }

    /**
     * member-service가 반환하는 계정 정보 DTO
     *
     * 예상 JSON:
     * { "accountId": "1001", "roleId": "ROLE_WH_MANAGER" }
     */
    public static class MemberAccountInfo {

        private String accountId;
        private String roleId;

        public MemberAccountInfo() {}

        public String getAccountId() { return accountId; }
        public String getRoleId() { return roleId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
    }
}
