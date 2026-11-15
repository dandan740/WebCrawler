package org.example;

import java.net.URI;

final class URIinfo {
    final URI uri;
    final int depth;

    URIinfo(URI uri, int depth) {
        this.uri = uri;
        this.depth = depth;
    }

    URIinfo(String value, int depth) {
        this(URI.create(value), depth);
    }
}
