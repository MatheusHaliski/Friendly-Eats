package com.friendlyeats.domain.model;

/**
 * Os feeds independentes do app: perfis (RF15), posts de comida e alertas sanitários (RF14). O feed de um post deriva
 * do tipo ({@link PostKind#feed()}); {@code PROFILE} lista contas, nunca posts.
 */
public enum FeedType {
    PROFILE,
    POST,
    ALERT
}
