package com.shale.core.service;

import java.util.List;

/** Persistence boundary for spelling terms owned by one authenticated Shale user. */
public interface UserDictionaryServicePort {
    record UserDictionaryWord(long id, int shaleClientId, int userId, String word, String normalizedWord) { }

    List<UserDictionaryWord> listWords(int shaleClientId, int userId, int actorUserId);

    UserDictionaryWord addWord(int shaleClientId, int userId, String word, int actorUserId);

    void removeWord(int shaleClientId, int userId, String normalizedWord, int actorUserId);
}
