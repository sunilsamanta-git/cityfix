package com.cityfix.model;

/** Each category knows which department normally handles it (null = admin decides). */
public enum Category {
    POTHOLE("Roads & Infrastructure"),
    STREETLIGHT("Electrical"),
    WATER_SUPPLY("Water Supply"),
    DRAINAGE("Sanitation"),
    GARBAGE("Sanitation"),
    OTHER(null);

    private final String defaultDepartment;

    Category(String defaultDepartment) { this.defaultDepartment = defaultDepartment; }

    public String getDefaultDepartment() { return defaultDepartment; }
}
