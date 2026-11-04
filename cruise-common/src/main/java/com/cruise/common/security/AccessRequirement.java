package com.cruise.common.security;

public record AccessRequirement(Mode mode, Permission permission) {
    public enum Mode { PUBLIC, AUTHENTICATED, PERMISSION, DENY }
    public static AccessRequirement publicAccess() { return new AccessRequirement(Mode.PUBLIC, null); }
    public static AccessRequirement authenticated() { return new AccessRequirement(Mode.AUTHENTICATED, null); }
    public static AccessRequirement require(Permission permission) {
        return new AccessRequirement(Mode.PERMISSION, java.util.Objects.requireNonNull(permission));
    }
    public static AccessRequirement deny() { return new AccessRequirement(Mode.DENY, null); }
}
