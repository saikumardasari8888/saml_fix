
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PCBrowserClient {

    private static final String DEFAULT_SSO_URL = "https://aontpfg6m.accounts.cloud.sap/saml2/idp/sso/aontpfg6m.accounts.ondemand.com";
    private static final Pattern LOCATION_URL_PATTERN = Pattern.compile("action=\"(.*?)\"");
    private static final Pattern SAML_REDIRECT_FORM_PATTERN = Pattern.compile("<form id=\"samlRedirect\".*action=\"([^\"]*)\"");
    private static final Pattern SAML_RESPONSE_PATTERN = Pattern.compile("id=\"SAMLResponse\" value=\"([^\"]*)\"");
    private static final Pattern AUTHENTICITY_TOKEN_PATTERN = Pattern.compile("name=\"authenticity_token\".*value=\"([^\"]*)\"");
    private static final Pattern RELAY_STATE_PATTERN = Pattern.compile("name=\"RelayState\".*value=\"([^\"]*)\"");

    private final RestTemplate restTemplate;
    private Map<String, String> csrfTokenMap = new HashMap<>();
    private BasicCookieStore cookieStore = new BasicCookieStore();

    public PCBrowserClient() {
        this.restTemplate = new RestTemplate(HttpClientUtil.getHttplientFactoryWithFollowRedirects(cookieStore));
    }

    public ResponseEntity<String> execute(PCConnConfig config, HttpMethod method, String path, HttpHeaders headers, Object body) {
        try {
            URI uri = new URI(config.getURL() + path);
            HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);

            // 1. Try direct request first
            ResponseEntity<String> initialResponse = restTemplate.exchange(uri, method, requestEntity, String.class);

            // 2. If authenticated, return directly
            if (!requiresAuthentication(initialResponse.getBody())) {
                return initialResponse;
            }

            // 3. Else perform SAML login flow
            log.info("Session not authenticated. Triggering SAML login flow...");
            ResponseEntity<String> authStart = sendInitialRequest(config, path);
            ResponseEntity<String> authResult = handleAuthRedirects(config, path, authStart, 1);

            HttpHeaders newHeaders = new HttpHeaders();
            newHeaders.putAll(authResult.getHeaders());
            if (headers != null) newHeaders.addAll(headers);

            HttpEntity<?> authenticatedRequest = new HttpEntity<>(body, newHeaders);
            return restTemplate.exchange(uri, method, authenticatedRequest, String.class);

        } catch (Exception e) {
            log.error("Failed to execute request to {} {}", method, path, e);
            throw new RuntimeException(e);
        }
    }

    public String getCsrfToken(PCConnConfig config, String path) throws URISyntaxException {
        String tokenUrl = config.getURL() + path;
        if ( csrfTokenMap.containsKey(tokenUrl) ) {
            return csrfTokenMap.get(tokenUrl);
        }
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("x-csrf-token", "Fetch");
        httpHeaders.add("Host",new URI(config.getURL()).getHost());
        ResponseEntity<String> res = execute(config, HttpMethod.HEAD, path, httpHeaders, null);
        if ( res.getStatusCode() == HttpStatus.OK ) {
            String csrfToken = res.getHeaders().getFirst("x-csrf-token");
            csrfTokenMap.put(tokenUrl, csrfToken);
            return csrfToken;
        } else {
            throw new IllegalArgumentException("Failed to fetch csrf token for path :"+tokenUrl);
        }
    }

    private boolean requiresAuthentication(String body) {
        if (body == null) return true;
        return body.contains("logOnForm") || body.contains("SAMLRequest") || body.contains("authenticity_token");
    }

    private ResponseEntity<String> sendInitialRequest(PCConnConfig config, String path) throws Exception {
        URI uri = new URI(config.getURL() + path);
        return restTemplate.exchange(new RequestEntity<>(HttpMethod.GET, uri), String.class);
    }

    private ResponseEntity<String> handleAuthRedirects(PCConnConfig config, String path, ResponseEntity<String> response, int attempt) throws Exception {
        String body = extractBody(response);
        String authUrl = extractFirstMatch(body, LOCATION_URL_PATTERN, null);
        if (authUrl == null) return response;

        ResponseEntity<String> intermediateResponse = submitFirstAuthForm(config, path, body, authUrl, null);
        String samlRedirectUrl = extractFirstMatch(intermediateResponse.getBody(), SAML_REDIRECT_FORM_PATTERN, null);
        if (samlRedirectUrl == null) return intermediateResponse;

        ResponseEntity<String> samlResponse = handleSamlRedirect(config, intermediateResponse.getBody(), samlRedirectUrl);
        return finalizeSession(config, path, samlResponse.getBody(), null);
    }

    private ResponseEntity<String> submitFirstAuthForm(PCConnConfig config, String path, String responseBody, String authUrl, List<String> cookies) throws Exception {
        MultiValueMap<String, String> form = extractFormInputs(responseBody);
        ResponseEntity<String> response = submitForm(config, path, form, authUrl, cookies);
        MultiValueMap<String, String> loginForm = extractLoginForm(config, response);
        String redirectLocation = extractLocationFromResponse(config, path, loginForm, authUrl, null);
        return executeRedirect(config, redirectLocation, new HttpHeaders());
    }

    private ResponseEntity<String> finalizeSession(PCConnConfig config, String path, String body, List<String> cookies) throws Exception {
        MultiValueMap<String, String> form = extractFormInputs(body);
        String url = config.getURL() + path;
        ResponseEntity<String> response = submitForm(config, path, form, url, cookies);
        URI uri = new URI(config.getURL()+path);
        return restTemplate.exchange(new RequestEntity<>(response.getHeaders(), HttpMethod.GET, uri), String.class);
    }

    private ResponseEntity<String> handleSamlRedirect(PCConnConfig config, String body, String samlUrl) {
        MultiValueMap<String, String> samlData = new LinkedMultiValueMap<>();
        samlData.add("authenticity_token", extractFirstMatch(body, AUTHENTICITY_TOKEN_PATTERN, null));
        samlData.add("SAMLResponse", extractFirstMatch(body, SAML_RESPONSE_PATTERN, null));
        samlData.add("RelayState", extractFirstMatch(body, RELAY_STATE_PATTERN, null));

        return restTemplate.postForEntity(samlUrl, new HttpEntity<>(samlData, new HttpHeaders()), String.class);
    }

    private ResponseEntity<String> submitForm(PCConnConfig config, String path, MultiValueMap<String, String> data, String url, List<String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        if (CollectionUtils.isNotEmpty(cookies)) {
            headers.add("Cookie", String.join("; ", cookies));
        }

        String targetUrl = isAbsoluteUrl(url) ? url : config.getURL()+path;
        ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, new HttpEntity<>(data, headers), String.class);
        if (response.getBody() != null && response.getBody().contains("Sorry, we could not authenticate you")) {
            throw new RuntimeException("Login/password incorrect.");
        }
        return response;
    }

    private ResponseEntity<String> executeRedirect(PCConnConfig config, String url, HttpHeaders headers) throws Exception {
        return restTemplate.exchange(new RequestEntity<>(headers, HttpMethod.GET, new URI(url)), String.class);
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

    private String extractLocationFromResponse(PCConnConfig config, String path, MultiValueMap<String, String> loginForm, String url, List<String> cookies) {
        ResponseEntity<String> response = submitForm(config, path, loginForm, url, cookies);
        String location = response.getHeaders().getFirst("Location");
        if (location == null && response.getBody() != null) {
            loginForm = extractLoginForm(config, response);
            response = submitForm(config, path, loginForm, url, cookies);
            location = response.getHeaders().getFirst("Location");
        }
        if (location == null) throw new RuntimeException("Missing Location header in response.");
        return location;
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

    public static void main(String[] args) throws Exception{
        String hostUrl = "https://my427446.s4hana.cloud.sap";
        //String exportCustTRs = "https://my427446.s4hana.cloud.sap/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/Requests?sap-client=100";
        PCConnConfig config = new PCConnConfig(hostUrl, "rahul.p@releaseowl.com", "Pippalla#301");
        PCBrowserClient obj = new PCBrowserClient();
        //System.out.println(obj.getCsrfToken(config, "/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/"));
        System.out.println(obj.getCsrfToken(config, "/sap/opu/odata/sap/UI_CUSTOMIZING_REQ_M_O2/"));
        for(int i=0;i<10;i++) {
            ResponseEntity<String> response = obj.execute(config, HttpMethod.GET, "/sap/opu/odata/sap/APS_EXT_ATO_EXP_SRV", new HttpHeaders(), null);
            System.out.println(response.getBody());
            Thread.sleep(2000L);
        }
    }
}
