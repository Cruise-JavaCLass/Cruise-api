package com.cruise.common.security;

@FunctionalInterface
public interface AccessPolicy {
    AccessRequirement requirement(String method, String path);
}
