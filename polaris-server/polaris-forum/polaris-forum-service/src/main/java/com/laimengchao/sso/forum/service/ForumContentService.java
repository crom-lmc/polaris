package com.laimengchao.sso.forum.service;


public interface ForumContentService {

    String sanitize(String value);

    String plainText(String html);
}
