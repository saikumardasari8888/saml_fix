package com.saparate.pc.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Slf4j
public class HttpClientUtil {
    private static final long CONNECTION_REQ_TIMEOUT = 10000;

    public static HttpComponentsClientHttpRequestFactory getHttplientFactoryWithFollowRedirects(BasicCookieStore cookieStore) {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_REQ_TIMEOUT))
                .build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig).setDefaultCookieStore(cookieStore);
        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }

    public static HttpComponentsClientHttpRequestFactory getHttplientFactoryWithoutFollowRedirects(BasicCookieStore cookieStore) {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_REQ_TIMEOUT))
                .build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore)
                .disableRedirectHandling();
        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }

    public static HttpComponentsClientHttpRequestFactory getHttpClienttFactoryWithNoCookies() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_REQ_TIMEOUT))
                .build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig)
                .disableCookieManagement()
                .disableRedirectHandling();
        return new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }

}
