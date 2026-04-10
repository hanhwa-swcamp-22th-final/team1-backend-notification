package com.conk.notification.query.controller;

import com.conk.notification.command.application.service.NotificationReadService;
import com.conk.notification.common.exception.BusinessException;
import com.conk.notification.common.exception.ErrorCode;
import com.conk.notification.query.service.NotificationQueryService;
import com.conk.notification.query.dto.NotificationListItemResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationReadService notificationReadService;

    public NotificationController(
            NotificationQueryService notificationQueryService,
            NotificationReadService notificationReadService
    ) {
        this.notificationQueryService = notificationQueryService;
        this.notificationReadService = notificationReadService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationListItemResponse>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "isRead", required = false) Boolean isRead,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        String resolvedAccountId = resolveAccountId(accountId, accountIdParam);
        List<NotificationListItemResponse> response = notificationQueryService.findNotifications(
                resolvedAccountId,
                page,
                size,
                type,
                isRead,
                sort
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String notificationId,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam
    ) {
        String resolvedAccountId = resolveAccountId(accountId, accountIdParam);
        notificationReadService.markAsRead(notificationId, resolvedAccountId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestParam(value = "accountId", required = false) String accountIdParam
    ) {
        String resolvedAccountId = resolveAccountId(accountId, accountIdParam);
        notificationReadService.markAllAsRead(resolvedAccountId);
        return ResponseEntity.noContent().build();
    }

    private String resolveAccountId(String accountId, String accountIdParam) {
        String resolvedAccountId = (accountId != null) ? accountId : accountIdParam;
        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            throw new BusinessException(
                    ErrorCode.MISSING_ACCOUNT_ID,
                    "accountId가 필요합니다. (헤더 X-User-Id 또는 쿼리 파라미터 accountId)"
            );
        }
        return resolvedAccountId;
    }
}
