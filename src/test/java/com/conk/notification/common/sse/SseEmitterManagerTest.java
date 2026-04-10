package com.conk.notification.common.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("SseEmitterManager 단위 테스트")
class SseEmitterManagerTest {

    private final SseEmitterManager sseEmitterManager = new SseEmitterManager();

    @Test
    @DisplayName("SSE 연결을 등록하면 emitter를 저장한다")
    void register_storesEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);

        sseEmitterManager.register("1001", emitter);

        assertThat(sseEmitterManager.get("1001")).isSameAs(emitter);
        assertThat(sseEmitterManager.getConnectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 accountId로 다시 등록하면 기존 emitter를 완료 처리하고 교체한다")
    void register_replacesExistingEmitter_whenAccountIdAlreadyExists() {
        SseEmitter existing = mock(SseEmitter.class);
        SseEmitter replacement = mock(SseEmitter.class);
        sseEmitterManager.register("1001", existing);

        sseEmitterManager.register("1001", replacement);

        verify(existing).complete();
        assertThat(sseEmitterManager.get("1001")).isSameAs(replacement);
        assertThat(sseEmitterManager.getConnectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("completion 콜백이 실행되면 emitter를 제거한다")
    void register_removesEmitter_whenCompletionCallbackRuns() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> completionCallback = new AtomicReference<>();
        captureCallbacks(emitter, completionCallback, new AtomicReference<>(), new AtomicReference<>());

        sseEmitterManager.register("1001", emitter);
        completionCallback.get().run();

        assertThat(sseEmitterManager.get("1001")).isNull();
    }

    @Test
    @DisplayName("timeout 콜백이 실행되면 emitter를 제거한다")
    void register_removesEmitter_whenTimeoutCallbackRuns() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> timeoutCallback = new AtomicReference<>();
        captureCallbacks(emitter, new AtomicReference<>(), timeoutCallback, new AtomicReference<>());

        sseEmitterManager.register("1001", emitter);
        timeoutCallback.get().run();

        assertThat(sseEmitterManager.get("1001")).isNull();
    }

    @Test
    @DisplayName("error 콜백이 실행되면 emitter를 제거한다")
    void register_removesEmitter_whenErrorCallbackRuns() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Consumer<Throwable>> errorCallback = new AtomicReference<>();
        captureCallbacks(emitter, new AtomicReference<>(), new AtomicReference<>(), errorCallback);

        sseEmitterManager.register("1001", emitter);
        errorCallback.get().accept(new RuntimeException("connection closed"));

        assertThat(sseEmitterManager.get("1001")).isNull();
    }

    private void captureCallbacks(
            SseEmitter emitter,
            AtomicReference<Runnable> completionCallback,
            AtomicReference<Runnable> timeoutCallback,
            AtomicReference<Consumer<Throwable>> errorCallback
    ) {
        doAnswer(invocation -> {
            completionCallback.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));
        doAnswer(invocation -> {
            timeoutCallback.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onTimeout(any(Runnable.class));
        doAnswer(invocation -> {
            errorCallback.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onError(any());
    }
}
