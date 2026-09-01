package com.shale.data.service.adapter;

import com.shale.core.service.UserDictionaryServicePort;
import com.shale.data.dao.UserDictionaryWordDao;
import java.util.List;
import java.util.Objects;

public final class UserDictionaryServiceAdapter implements UserDictionaryServicePort {
    private final UserDictionaryWordDao dao;
    public UserDictionaryServiceAdapter(UserDictionaryWordDao dao){this.dao=Objects.requireNonNull(dao,"dao");}
    @Override public List<UserDictionaryWord> listWords(int tenantId,int userId,int actorId){return dao.listWords(tenantId,userId,actorId);}
    @Override public UserDictionaryWord addWord(int tenantId,int userId,String word,int actorId){return dao.addWord(tenantId,userId,word,actorId);}
    @Override public void removeWord(int tenantId,int userId,String word,int actorId){dao.removeWord(tenantId,userId,word,actorId);}
}
