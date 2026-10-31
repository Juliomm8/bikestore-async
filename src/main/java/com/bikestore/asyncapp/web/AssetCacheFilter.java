package com.bikestore.asyncapp.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AssetCacheFilter extends OncePerRequestFilter {
    private static final String NO_STORE = "private, no-store, no-cache, must-revalidate";
    private static final String HTML_REVALIDATE = "no-cache, must-revalidate";
    private static final String ASSET_REVALIDATE = "public, max-age=0, must-revalidate";
    private static final String VERSIONED_ASSET = "public, max-age=31536000, immutable";
    private static final Set<String> LONG_LIVED_EXTENSIONS = Set.of("css", "js", "woff2", "webp");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (path.equals("/api") || path.startsWith("/api/")) {
            response.setHeader("Cache-Control", NO_STORE);
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        } else if (isHtml(path)) {
            response.setHeader("Cache-Control", HTML_REVALIDATE);
        } else if (isLongLivedAsset(path)) {
            response.setHeader("Cache-Control", hasVersion(request)
                    ? VERSIONED_ASSET
                    : ASSET_REVALIDATE);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isHtml(String path) {
        return path.equals("/") || path.equals("/index.html") || path.endsWith(".html");
    }

    private boolean isLongLivedAsset(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 && LONG_LIVED_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }

    private boolean hasVersion(HttpServletRequest request) {
        String version = request.getParameter("v");
        return version != null && !version.isBlank();
    }
}
