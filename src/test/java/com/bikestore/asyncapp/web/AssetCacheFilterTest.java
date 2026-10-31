package com.bikestore.asyncapp.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AssetCacheFilterTest {
    private final AssetCacheFilter filter = new AssetCacheFilter();

    @Test
    void cachesVersionedStaticResourcesImmutablyForOneYear() throws Exception {
        MockHttpServletRequest request = request("/images/alerce.webp");
        request.setParameter("v", "20261031-3");
        MockHttpServletResponse response = filter(request);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    void requiresRevalidationForUnversionedStaticResources() throws Exception {
        MockHttpServletResponse response = filter(request("/css/store.css"));

        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=0, must-revalidate");
    }

    @Test
    void preventsCachingApiResponsesAndRevalidatesHtml() throws Exception {
        MockHttpServletResponse apiResponse = filter(request("/api/orders"));
        MockHttpServletResponse htmlResponse = filter(request("/"));

        assertThat(apiResponse.getHeader("Cache-Control"))
                .isEqualTo("private, no-store, no-cache, must-revalidate");
        assertThat(apiResponse.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(htmlResponse.getHeader("Cache-Control")).isEqualTo("no-cache, must-revalidate");
    }

    private MockHttpServletResponse filter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setServletPath(path);
        return request;
    }
}
