package com.jpitsg.sysman;

final class HttpResult {
    final int code;
    final String body;

    HttpResult(int code, String body) {
        this.code = code;
        this.body = body == null ? "" : body;
    }

    boolean success() {
        return code >= 200 && code < 300;
    }
}
