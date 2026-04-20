package com.shale.ui.privacy;

public final class PhiFieldRegistry {
    private PhiFieldRegistry() {
    }

    public static boolean isPhi(String tableName, String fieldName) {
        return com.shale.core.privacy.PhiFieldRegistry.isPhi(tableName, fieldName);
    }

    public static boolean isPhiQualified(String tableDotField) {
        return com.shale.core.privacy.PhiFieldRegistry.isPhiQualified(tableDotField);
    }
}
