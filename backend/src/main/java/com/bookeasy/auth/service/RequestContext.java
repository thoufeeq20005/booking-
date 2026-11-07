package com.bookeasy.auth.service;

/** Caller metadata recorded with sessions and used as rate-limit keys. */
public record RequestContext(String ip, String userAgent) {
}
