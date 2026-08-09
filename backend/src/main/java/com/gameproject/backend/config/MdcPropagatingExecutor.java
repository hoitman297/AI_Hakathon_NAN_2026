package com.gameproject.backend.config;

import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.MDC;

/**
 * MDC(예: sessionId)는 ThreadLocal이라 다른 스레드로 작업을 넘기면 사라진다. 독립적인
 * LLM 호출을 병렬화하려고 별도 스레드에서 실행하면, LlmProxyRestClientConfig의
 * X-Session-Id 헤더(호출 스레드의 MDC.get()으로 채워짐)가 비어버려 backend-llm-proxy 간
 * 로그 상관관계 기능이 조용히 깨진다. 제출 시점의 MDC를 캡처해 실행 스레드에 그대로
 * 복원해준다.
 */
public class MdcPropagatingExecutor implements Executor {

    private final Executor delegate;

    public MdcPropagatingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        delegate.execute(() -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setContext(context);
            try {
                command.run();
            } finally {
                setContext(previous);
            }
        });
    }

    private void setContext(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
