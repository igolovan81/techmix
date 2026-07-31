package com.testingai.graphql.domain;

public record User(Long id, String username, String displayName, Role role) {
}
