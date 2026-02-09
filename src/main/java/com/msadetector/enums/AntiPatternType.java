package com.msadetector.enums;

public enum AntiPatternType {
    CYCLIC_DEPENDENCY("Cyclic Dependency", "Circular dependencies between services"),
    SHARED_DATABASE("Shared Database", "Multiple services accessing the same database"),
    NANO_SERVICE("Nano Service", "Service too small to justify operational overhead"),
    GOD_SERVICE("God Service", "Service handling too many responsibilities"),
    CHATTY_SERVICE("Chatty Service", "Excessive fine-grained communication between services"),
    HARDCODED_ENDPOINTS("Hardcoded Endpoints", "Service URLs hardcoded instead of using service discovery"),
    DISTRIBUTED_MONOLITH("Distributed Monolith", "Tightly coupled services that must be deployed together"),
    API_VERSIONING_ABSENCE("API Versioning Absence", "No API versioning strategy detected"),
    WRONG_CUTS("Wrong Cuts", "Misplaced service boundaries causing high coupling"),
    ESB_MISUSE("ESB Misuse", "Single service mediating most inter-service communication");

    private final String displayName;
    private final String description;

    AntiPatternType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
