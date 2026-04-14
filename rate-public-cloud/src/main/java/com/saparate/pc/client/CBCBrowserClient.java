
package com.saparate.pc.client;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import com.saparate.pc.config.PCConnConfig;
import com.saparate.pc.util.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CBCBrowserClient {

    private static final Pattern LOCATION_URL_PATTERN = Pattern.compile("location=\"(.*?)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("document\\.cookie\\s*=\\s*\"signature=([^\";]+)");

    private final RestTemplate restTemplateWithCookies;
    private final RestTemplate restTemplateNoCookies;
    private Map<String, String> xsrfTokenMap = new HashMap<>();
    private BasicCookieStore cookieStore = new BasicCookieStore();

    public CBCBrowserClient() {
        this.restTemplateWithCookies = new RestTemplate(HttpClientUtil.getHttplientFactoryWithoutFollowRedirects(cookieStore));
        this.restTemplateNoCookies = new RestTemplate(HttpClientUtil.getHttpClienttFactoryWithNoCookies());
    }

    public ResponseEntity<String> execute(PCConnConfig config, HttpMethod method, String path, HttpHeaders inputHeaders, Object body) {
        try {
            URI uri = new URI(config.getURL() + path);
            HttpEntity<?> requestEntity = new HttpEntity<>(body, inputHeaders);
            ResponseEntity<String> initialResponse = restTemplateWithCookies.exchange(uri, method, requestEntity, String.class);
            // 2. If authenticated, return directly
            if (!requiresAuthentication(initialResponse.getBody())) {
                return initialResponse;
            }
            HttpHeaders headers = new HttpHeaders();
            String signature = extractSignature(initialResponse.getBody());
            // 3. Else perform SAML login flow
            log.info("Session not authenticated. Triggering SAML login flow...");
            ResponseEntity<String> authStart = sendInitialRequest(config, path);
            ResponseEntity<String> authResult = handleAuthRedirects(config, path, signature, authStart, headers, 1);

            updateCoockieStore(uri, authResult, cookieStore);

            HttpEntity<?> authenticatedRequest = new HttpEntity<>(body, headers);
            return restTemplateWithCookies.exchange(uri, method, authenticatedRequest, String.class);
        } catch (Exception e) {
            log.error("Failed to execute request to {} {}", method, path, e);
            throw new RuntimeException(e);
        }
    }

    public String getXsrfToken(PCConnConfig config) throws URISyntaxException {
        String host = new URI(config.getURL()).getHost();
        if ( xsrfTokenMap.containsKey(host) ) {
            return xsrfTokenMap.get(host);
        }
        HttpHeaders httpHeaders = new HttpHeaders();
        ResponseEntity<String> res = execute(config, HttpMethod.GET, "/sap/af/system/config", httpHeaders, null);
        if ( res.getStatusCode() == HttpStatus.OK ) {
            String xsrfToken = res.getHeaders().getFirst("sap-xsrf");
            xsrfTokenMap.put(host, xsrfToken);
            return xsrfToken;
        } else {
            throw new IllegalArgumentException("Failed to fetch sap-xsrf token for the host :"+host);
        }
    }

    private boolean requiresAuthentication(String body) {
        if (body == null) return false;
        return body.contains("logOnForm") || body.contains("SAMLRequest") || body.contains("authenticity_token") || body.contains("location");
    }

    private ResponseEntity<String> sendInitialRequest(PCConnConfig config, String path) throws Exception {
        URI uri = new URI(config.getURL() + path);
        return restTemplateWithCookies.exchange(new RequestEntity<>(HttpMethod.GET, uri), String.class);
    }

    private ResponseEntity<String> handleAuthRedirects(PCConnConfig config, String path, String signature, ResponseEntity<String> response, HttpHeaders headers, int attempt) throws Exception {
        String body = extractBody(response);
        String authUrl = extractFirstMatch(body, LOCATION_URL_PATTERN, null);
        if (authUrl == null) return response;

        ResponseEntity<String> res = executeRedirect(config, authUrl, headers);
        String loginUrl = res.getHeaders().getFirst("Location");

        headers.add(HttpHeaders.ACCEPT, "text/html");
        ResponseEntity<String> logiUrlnRes = restTemplateWithCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(loginUrl)), String.class);

        String ssoAuthUrl = logiUrlnRes.getHeaders().getFirst("Location");
        headers = new HttpHeaders();
        ResponseEntity<String> ssoAuthUrRes = restTemplateWithCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(ssoAuthUrl)), String.class);

        ResponseEntity<String> intermediateResponse = submitFirstAuthForm(config, ssoAuthUrRes, ssoAuthUrl, null);
        headers = new HttpHeaders();
        String callbackUrl = intermediateResponse.getHeaders().getFirst("location");
        String domain = new URI(callbackUrl).getHost();
        StringBuilder cookieHeader = new StringBuilder();
        for (Cookie cookie : cookieStore.getCookies()) {
            if (domain.equalsIgnoreCase(cookie.getDomain())) {
                cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
            }
        }
        cookieHeader.append("fragmentAfterLogin=; locationAfterLogin=").append(URLEncoder.encode(path, StandardCharsets.UTF_8));
        cookieHeader.append("; signature=").append(URLEncoder.encode(signature, StandardCharsets.UTF_8));
        headers.add("Cookie", cookieHeader.toString());

        return restTemplateNoCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(callbackUrl)), String.class);
    }

    private ResponseEntity<String> submitFirstAuthForm(PCConnConfig config, ResponseEntity<String> response, String authUrl, List<String> cookies) throws Exception {
        MultiValueMap<String, String> form = extractLoginForm(config, response);
        ResponseEntity<String> response2 = submitForm(config, form, authUrl, cookies);
        return response2;
    }

    public static String extractSignature(String html) {
        Matcher matcher = SIGNATURE_PATTERN.matcher(html);
        if (matcher.find()) {
            String rawSignature = matcher.group(1);
            return java.net.URLDecoder.decode(rawSignature, java.nio.charset.StandardCharsets.UTF_8);
        }
        return "";
    }

    private ResponseEntity<String> submitForm(PCConnConfig config, MultiValueMap<String, String> data, String url, List<String> cookies) throws Exception{
        HttpHeaders headers = new HttpHeaders();
        if (CollectionUtils.isNotEmpty(cookies)) {
            headers.add("Cookie", String.join("; ", cookies));
        }
        List<String> actionUrl = data.get("idpSSOEndpoint");
        String targetUrl = !actionUrl.isEmpty() ? actionUrl.getFirst() : null;
        if (ObjectUtils.isEmpty(targetUrl) ) {
            targetUrl = isAbsoluteUrl(url) ? url : config.getURL();
        }
        headers.add(HttpHeaders.ACCEPT, "test/html");
        ResponseEntity<String> response = restTemplateWithCookies.postForEntity(targetUrl, new HttpEntity<>(data, headers), String.class);
        if( response.getStatusCode().value() == HttpStatus.FOUND.value() ) {
            String redirectUrl = response.getHeaders().getFirst("Location");
            ResponseEntity<String> redirectUrlRes = restTemplateWithCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(redirectUrl)), String.class);
            if ( response.getStatusCode().value() == HttpStatus.FOUND.value() ) {
                String redirectUrl2 = redirectUrlRes.getHeaders().getFirst("Location");
                ResponseEntity<String> redirectUrl2Res = restTemplateWithCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(redirectUrl2)), String.class);
                return redirectUrl2Res;
            }
        } else {
            if (response.getBody() != null && response.getBody().contains("Sorry, we could not authenticate you")) {
                throw new RuntimeException("Login/password incorrect.");
            }
        }
        return response;
    }

    private ResponseEntity<String> executeRedirect(PCConnConfig config, String url, HttpHeaders headers) throws Exception {
        return restTemplateWithCookies.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(url)), String.class);
    }

    private MultiValueMap<String, String> extractFormInputs(String html) {
        Document doc = Jsoup.parse(html);
        Element form = doc.selectFirst("form");
        if (form == null) throw new IllegalStateException("No form found in HTML");

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (Element input : form.select("input")) {
            map.put(input.attr("name"), Collections.singletonList(input.attr("value")));
        }
        return map;
    }

    private MultiValueMap<String, String> extractLoginForm(PCConnConfig config, ResponseEntity<String> response) {
        Document doc = Jsoup.parse(response.getBody());
        Element form = doc.getElementById("logOnForm");
        if (form == null) {
            log.error(String.format("Response code: %s ,Headers: %s ,Body: %s",response.getStatusCode(), response.getHeaders(), response.getBody()));
            throw new IllegalStateException("logOnForm not found. Check if Universal ID is in use.");
        }

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (Element input : form.select("input")) {
            String key = input.attr("name");
            String value = switch (key) {
                case "j_username" -> config.getUsername();
                case "j_password" -> config.getPassword();
                default -> input.attr("value");
            };
            map.put(key, Collections.singletonList(value));
        }
        return map;
    }

    private String extractBody(ResponseEntity<?> response) {
        Object body = response.getBody();
        if (body instanceof String) return (String) body;
        if (body instanceof byte[]) return new String((byte[]) body);
        throw new IllegalArgumentException("Unsupported response body type: " + body);
    }

    private String extractFirstMatch(String content, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private boolean isAbsoluteUrl(String url) {
        try {
            return new URI(url).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateCoockieStore(URI uri, ResponseEntity<String> response, BasicCookieStore cookieStore) {
        // Extract Set-Cookie headers
        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        if (setCookieHeaders == null) {
            return;
        }

        String domain = uri.getHost();
        String path = uri.getPath().isEmpty() ? "/" : uri.getPath();
        boolean secure = uri.getScheme().equalsIgnoreCase("https");

        for (String header : setCookieHeaders) {
            try {
                // Parse the cookie name=value from the Set-Cookie header
                String[] parts = header.split(";", 2);
                String nameValue = parts[0].trim();
                int eqIdx = nameValue.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String name = nameValue.substring(0, eqIdx).trim();
                String value = nameValue.substring(eqIdx + 1).trim();

                BasicClientCookie cookie = new BasicClientCookie(name, value);
                cookie.setDomain(domain);
                cookie.setPath(path);
                cookie.setSecure(secure);
                cookieStore.addCookie(cookie);
            } catch (Exception e) {
                log.warn("Failed to parse Set-Cookie header: {}", header, e);
            }
        }
    }

    public static void main(String[] args) throws Exception{
        //String url = "https://my427446.s4hana.cloud.sap/sap/opu/odata/sap/APS_EXT_ATO_EXP_SRV";
        String exportCustTRs = "https://my83660018.prod04.cbc.ap.one.cloud.sap";
        PCConnConfig config = new PCConnConfig(exportCustTRs, "rahul.p@releaseowl.com", "Pippalla#301");
        CBCBrowserClient obj = new CBCBrowserClient();
        for(int i=0;i<10;i++) {
            ResponseEntity<String> response = obj.execute(config, HttpMethod.GET, "/sap/af/system/config", new HttpHeaders(), null);
            System.out.println(response.getBody());
            Thread.sleep(1500L);
        }
        String sapXsrfToken = obj.getXsrfToken(config);
        System.out.println(sapXsrfToken);
    }
}
