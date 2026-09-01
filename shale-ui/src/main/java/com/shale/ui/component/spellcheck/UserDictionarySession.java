package com.shale.ui.component.spellcheck;

import com.shale.core.service.UserDictionaryServicePort;
import com.shale.core.util.DictionaryWordNormalizer;
import com.shale.ui.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/** One authenticated-session cache; persistence is accessed only through the core port. */
public final class UserDictionarySession {
    private static final Logger LOG=LoggerFactory.getLogger(UserDictionarySession.class);
    private static volatile UserDictionarySession current=offline();
    private final UserDictionaryServicePort service;
    private final AppState state;
    private final LocalSpellChecker checker=ShaleDictionary.create();
    private int loadedTenant=-1,loadedUser=-1;

    public UserDictionarySession(UserDictionaryServicePort service,AppState state){this.service=Objects.requireNonNull(service);this.state=Objects.requireNonNull(state);}
    private UserDictionarySession(){service=null;state=null;}
    private static UserDictionarySession offline(){return new UserDictionarySession();}
    public static void configure(UserDictionarySession session){current=Objects.requireNonNull(session);}
    public static UserDictionarySession current(){return current;}
    public LocalSpellChecker checker(){return checker;}

    /** Loads once for the active identity. Failures leave bundled spellchecking available. */
    public synchronized void load() {
        if(service==null)return; int tenant=tenant(),user=user(); if(tenant==loadedTenant&&user==loadedUser)return;
        checker.clearCustomDictionary();
        try { List<UserDictionaryServicePort.UserDictionaryWord> words=service.listWords(tenant,user,user);
            words.forEach(w->checker.addToCustomDictionary(w.normalizedWord())); loadedTenant=tenant;loadedUser=user;
        } catch(RuntimeException ex){LOG.warn("Custom dictionary unavailable; bundled spellcheck remains active",ex);}
    }
    public synchronized void add(String word) {
        String normalized=DictionaryWordNormalizer.normalize(word);if(normalized.isBlank())throw new IllegalArgumentException("Dictionary word must not be blank.");
        if(service==null)throw new IllegalStateException("Sign in to save custom dictionary words.");
        UserDictionaryServicePort.UserDictionaryWord saved=service.addWord(tenant(),user(),word,user());
        checker.addToCustomDictionary(saved.normalizedWord());
    }
    public synchronized void remove(String word) {
        String normalized=DictionaryWordNormalizer.normalize(word);if(normalized.isBlank())throw new IllegalArgumentException("Dictionary word must not be blank.");
        if(service==null)throw new IllegalStateException("Sign in to change custom dictionary words.");
        service.removeWord(tenant(),user(),normalized,user());checker.removeFromCustomDictionary(normalized);
    }
    public synchronized List<UserDictionaryServicePort.UserDictionaryWord> list(){if(service==null)return List.of();load();int user=user();return service.listWords(tenant(),user,user);}
    private int tenant(){Integer value=state.getShaleClientId();if(value==null||value<=0)throw new SecurityException("An authenticated tenant is required.");return value;}
    private int user(){Integer value=state.getUserId();if(value==null||value<=0)throw new SecurityException("An authenticated user is required.");return value;}
}
