package com.jpitsg.sysman;

final class TaskResult {
    final boolean success;
    final String message;

    private TaskResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    static TaskResult success(String message) {
        return new TaskResult(true, message);
    }

    static TaskResult failure(String message) {
        return new TaskResult(false, message);
    }
}
