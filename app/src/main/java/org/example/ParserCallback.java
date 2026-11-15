package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class ParserCallback {
    private static final int CONNECTION_TIMEOUT_MILLIS = 10_000;

    private ParserCallback() {
    }

    static PageResult fetchAndAnalyze(URIinfo page) {
        try {
            Connection connection = Jsoup.connect(page.uri.toString())
                    .timeout(CONNECTION_TIMEOUT_MILLIS)
                    .userAgent("WebCrawler/1.0");
            Document document = connection.get();
            document.select("script, style, noscript, template, [hidden]").remove();

            Map<String, Long> wordCounts = countWords(document.body().text());
            List<URI> links = findLinks(document);
            return new PageResult(page.uri, page.depth, wordCounts, links, null);
        } catch (IOException e) {
            return new PageResult(page.uri, page.depth, Map.of(), List.of(), e.getMessage());
        }
    }

    private static Map<String, Long> countWords(String text) {
        Map<String, Long> counts = new HashMap<>();
        for (String token : text.split("\\s+")) {
            if (!token.isEmpty()) {
                counts.merge(token, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static List<URI> findLinks(Document document) {
        List<URI> links = new ArrayList<>();
        for (var element : document.select("a[href], area[href], frame[src], iframe[src]")) {
            String attribute = element.hasAttr("href") ? "href" : "src";
            try {
                URI resolved = URI.create(document.baseUri()).resolve(element.attr(attribute));
                String scheme = resolved.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http")
                        && !scheme.equalsIgnoreCase("https"))) {
                    continue;
                }
                links.add(normalizeHttpUri(resolved));
            } catch (IllegalArgumentException | URISyntaxException e) {
                System.err.println("Preskakujeme spatny link: " + element.attr(attribute)
                        + " (" + e.getMessage() + ")");
            }
        }
        return links;
    }

    static URI normalizeHttpUri(URI uri) throws URISyntaxException {
        URI normalized = uri.normalize();
        String scheme = normalized.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || normalized.getHost() == null) {
            throw new IllegalArgumentException("Jsou podporovany pouze absolutni URL ve formatu HTTP nebo HTTPS.");
        }
        int port = normalized.getPort();
        if ((scheme.equalsIgnoreCase("http") && port == 80)
                || (scheme.equalsIgnoreCase("https") && port == 443)) {
            port = -1;
        }
        return new URI(scheme.toLowerCase(Locale.ROOT), normalized.getUserInfo(),
                normalized.getHost().toLowerCase(Locale.ROOT), port,
                normalized.getPath().isEmpty() ? "/" : normalized.getPath(),
                normalized.getQuery(), null).normalize();
    }

    static final class PageResult {
        final URI uri;
        final int depth;
        final Map<String, Long> wordCounts;
        final List<URI> links;
        final String error;

        PageResult(URI uri, int depth, Map<String, Long> wordCounts,
                List<URI> links, String error) {
            this.uri = uri;
            this.depth = depth;
            this.wordCounts = wordCounts;
            this.links = links;
            this.error = error;
        }
    }
}
