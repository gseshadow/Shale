package com.shale.ui.component.spellcheck;

import com.shale.core.service.UserDictionaryServicePort;
import com.shale.core.util.DictionaryWordNormalizer;
import com.shale.ui.state.AppState;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UserDictionarySessionTest {
    @Test void addIsNormalizedPersistentIdempotentAndRemovableAcrossSessions() {
        MemoryPort port=new MemoryPort(); AppState state=state(7,11);
        String customWord="ShaleOrthopedicsNonce";
        UserDictionarySession first=new UserDictionarySession(port,state);first.load();assertTrue(first.checker().isMisspelled(customWord));
        first.add(" "+customWord+" ");first.add(customWord.toLowerCase(Locale.ROOT));
        assertFalse(first.checker().isMisspelled(customWord.toUpperCase(Locale.ROOT)));assertEquals(1,port.listWords(7,11,11).size());
        UserDictionarySession reloaded=new UserDictionarySession(port,state);reloaded.load();assertFalse(reloaded.checker().isMisspelled(customWord));
        reloaded.remove(customWord.toUpperCase(Locale.ROOT));UserDictionarySession afterRemove=new UserDictionarySession(port,state);afterRemove.load();assertTrue(afterRemove.checker().isMisspelled(customWord));
    }
    @Test void wordsAreUserAndTenantScopedAndIdentityChangesClearCache() {
        MemoryPort port=new MemoryPort();AppState a=state(7,11);UserDictionarySession session=new UserDictionarySession(port,a);session.add("AlbuquerqueOrthopedics");
        a.setUserId(12);session.load();assertTrue(session.checker().isMisspelled("AlbuquerqueOrthopedics"));assertTrue(port.listWords(7,12,12).isEmpty());
        assertTrue(port.listWords(8,11,11).isEmpty());
    }
    @Test void failedPersistenceNeverAcceptsWord() {
        AppState state=state(7,11);UserDictionarySession session=new UserDictionarySession(new MemoryPort(){@Override public UserDictionaryWord addWord(int t,int u,String w,int a){throw new RuntimeException("offline");}},state);
        assertThrows(RuntimeException.class,()->session.add("persistme"));assertTrue(session.checker().isMisspelled("persistme"));
    }
    @Test void ignoreRemainsSessionOnlyAndRemovalFallsBackToBundledLayers() {
        MemoryPort port=new MemoryPort();AppState state=state(7,11);UserDictionarySession session=new UserDictionarySession(port,state);
        session.checker().ignore("sessiononly");assertFalse(session.checker().isMisspelled("sessiononly"));
        UserDictionarySession reloaded=new UserDictionarySession(port,state);reloaded.load();assertTrue(reloaded.checker().isMisspelled("sessiononly"));
        reloaded.add("patient");reloaded.remove("patient");assertFalse(reloaded.checker().isMisspelled("patient"),"bundled Hunspell remains authoritative");
    }
    @Test void normalizationHandlesCurlyApostrophesAndBlanks(){assertEquals("don't",DictionaryWordNormalizer.normalize("  DON’T "));assertEquals("",DictionaryWordNormalizer.normalize("  "));}
    private static AppState state(int t,int u){AppState s=new AppState();s.setShaleClientId(t);s.setUserId(u);return s;}
    private static class MemoryPort implements UserDictionaryServicePort {
        private final Map<String,UserDictionaryWord> rows=new HashMap<>();private long id;
        private String key(int t,int u,String w){return t+":"+u+":"+DictionaryWordNormalizer.normalize(w);}
        public List<UserDictionaryWord> listWords(int t,int u,int a){if(u!=a)throw new SecurityException();return rows.values().stream().filter(w->w.shaleClientId()==t&&w.userId()==u).toList();}
        public UserDictionaryWord addWord(int t,int u,String w,int a){String n=DictionaryWordNormalizer.normalize(w);return rows.computeIfAbsent(key(t,u,n),x->new UserDictionaryWord(++id,t,u,w.strip(),n));}
        public void removeWord(int t,int u,String w,int a){rows.remove(key(t,u,w));}
    }
}
