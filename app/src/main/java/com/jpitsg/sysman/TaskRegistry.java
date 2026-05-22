package com.jpitsg.sysman;

final class TaskRegistry {
    private TaskRegistry() {
    }

    static SystemTask create(String taskId) {
        if (TaskIds.GPS_POST.equals(taskId)) {
            return new GpsPostTask();
        }
        return null;
    }
}
