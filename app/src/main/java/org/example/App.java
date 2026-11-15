package org.example;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.nio.charset.StandardCharsets;

public class App {
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int DEFAULT_DEBUG_LEVEL = 0;
    
    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        if (args.length < 1 || args.length > 3) {
            printUsage();
            return;
        }

        final URI startUri;
        final int maxDepth;
        final int debugLevel;
        try {
            startUri = ParserCallback.normalizeHttpUri(URI.create(args[0]));
            maxDepth = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_MAX_DEPTH;
            debugLevel = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_DEBUG_LEVEL;
            if (maxDepth < 0 || debugLevel < 0) {
                throw new IllegalArgumentException("Hloubka nemuze byt zaporna.");
            }
        } catch (IllegalArgumentException | URISyntaxException e) {
            System.err.println("Spatne parametry: " + e.getMessage());
            printUsage();
            return;
        }

        crawl(startUri, maxDepth, debugLevel);
    }

    private static void crawl(URI startUri, int maxDepth, int debugLevel) {
        int threadCount = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 16));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ExecutorCompletionService<ParserCallback.PageResult> completedPages =
                new ExecutorCompletionService<>(executor);
        Queue<URIinfo> foundURIs = new LinkedList<>();
        Set<String> visitedURIs = ConcurrentHashMap.newKeySet();
        Map<String, LongAdder> wordFrequencies = new HashMap<>();
        foundURIs.add(new URIinfo(startUri, 0));
        visitedURIs.add(startUri.toString());

        int inFlight = 0;
        try {
            while (!foundURIs.isEmpty() || inFlight > 0) {
                while (inFlight < threadCount && !foundURIs.isEmpty()) {
                    URIinfo page = foundURIs.remove();
                    completedPages.submit(() -> ParserCallback.fetchAndAnalyze(page));
                    inFlight++;
                }

                if (inFlight == 0) {
                    continue;
                }

                ParserCallback.PageResult result;
                try {
                    result = completedPages.take().get();
                } catch (java.util.concurrent.ExecutionException e) {
                    System.err.println("Chyba pri analyze stranky: " + e.getCause());
                    inFlight--;
                    continue;
                }
                inFlight--;
                if (result.error != null) {
                    System.err.println("Chyba pri nacitani " + result.uri + ": " + result.error);
                    continue;
                }

                if (debugLevel > 0) {
                    System.err.println("Analyzed " + result.uri);
                }
                result.wordCounts.forEach((word, count) ->
                        wordFrequencies.computeIfAbsent(word, ignored -> new LongAdder()).add(count));

                if (result.depth < maxDepth) {
                    for (URI link : result.links) {
                        if (visitedURIs.add(link.toString())) {
                            foundURIs.add(new URIinfo(link, result.depth + 1));
                            if (debugLevel > 1) {
                                System.err.println("Discovered " + link);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Prohledavani bylo preruseno.");
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Nektere pracovni procesy crawleru nebyly zastaveny v case.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Preruseno pri cekani na ukonceni crawleru.");
            }
        }

        printTopWords(wordFrequencies);
    }

    private static void printTopWords(Map<String, LongAdder> frequencies) {
        frequencies.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, LongAdder>>comparingLong(
                                entry -> entry.getValue().sum())
                        .reversed())
                .limit(20)
                .forEach(entry -> System.out.println(entry.getKey() + ";" + entry.getValue().sum()));
    }

    private static void printUsage() {
        System.err.println("Pouziti: WebCrawler <start-url> [max-depth=2] [debug-level=0]");
    }

    public String getGreeting() {
        return "Hello World!";
    }
}
