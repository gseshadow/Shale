package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.UserDictionaryServicePort.UserDictionaryWord;
import com.shale.core.util.DictionaryWordNormalizer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tenant- and user-scoped storage for custom spelling words. */
public final class UserDictionaryWordDao {
    private final DbSessionProvider db;

    public UserDictionaryWordDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db, "db"); }

    public List<UserDictionaryWord> listWords(int tenantId, int userId, int actorId) {
        requireScope(tenantId, userId); requireOwner(userId,actorId);
        String sql = "SELECT Id, ShaleClientId, UserId, Word, NormalizedWord FROM dbo.UserDictionaryWords "
                + "WHERE ShaleClientId=? AND UserId=? ORDER BY NormalizedWord";
        try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) {
            ps.setInt(1,tenantId); ps.setInt(2,userId);
            try (ResultSet rs=ps.executeQuery()) { List<UserDictionaryWord> result=new ArrayList<>();
                while(rs.next()) result.add(row(rs)); return List.copyOf(result); }
        } catch(SQLException e) { throw new RuntimeException("Failed to list custom dictionary words",e); }
    }

    public UserDictionaryWord addWord(int tenantId, int userId, String word, int actorId) {
        requireScope(tenantId,userId); requireOwner(userId,actorId);
        String display=word==null?"":word.strip(); String normalized=DictionaryWordNormalizer.normalize(word);
        if(normalized.isBlank()) throw new IllegalArgumentException("Dictionary word must not be blank.");
        String sql="INSERT INTO dbo.UserDictionaryWords (ShaleClientId,UserId,Word,NormalizedWord,CreatedByUserId,UpdatedByUserId) "
                + "OUTPUT inserted.Id,inserted.ShaleClientId,inserted.UserId,inserted.Word,inserted.NormalizedWord VALUES (?,?,?,?,?,?)";
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)) {
            ps.setInt(1,tenantId);ps.setInt(2,userId);ps.setString(3,display);ps.setString(4,normalized);ps.setInt(5,actorId);ps.setInt(6,actorId);
            try(ResultSet rs=ps.executeQuery()){rs.next();return row(rs);}
        } catch(SQLException e) {
            if(e.getErrorCode()==2601||e.getErrorCode()==2627) return find(tenantId,userId,normalized);
            throw new RuntimeException("Failed to add custom dictionary word",e);
        }
    }

    public void removeWord(int tenantId,int userId,String word,int actorId) {
        requireScope(tenantId,userId); requireOwner(userId,actorId);
        String normalized=DictionaryWordNormalizer.normalize(word); if(normalized.isBlank()) throw new IllegalArgumentException("Dictionary word must not be blank.");
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(
                "DELETE FROM dbo.UserDictionaryWords WHERE ShaleClientId=? AND UserId=? AND NormalizedWord=?")) {
            ps.setInt(1,tenantId);ps.setInt(2,userId);ps.setString(3,normalized);ps.executeUpdate();
        } catch(SQLException e){throw new RuntimeException("Failed to remove custom dictionary word",e);}
    }

    private UserDictionaryWord find(int tenantId,int userId,String normalized) {
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(
                "SELECT Id,ShaleClientId,UserId,Word,NormalizedWord FROM dbo.UserDictionaryWords WHERE ShaleClientId=? AND UserId=? AND NormalizedWord=?")) {
            ps.setInt(1,tenantId);ps.setInt(2,userId);ps.setString(3,normalized);
            try(ResultSet rs=ps.executeQuery()){if(rs.next())return row(rs);throw new IllegalStateException("Concurrent dictionary add was not visible.");}
        }catch(SQLException e){throw new RuntimeException("Failed to read custom dictionary word",e);}
    }
    private static UserDictionaryWord row(ResultSet rs)throws SQLException{return new UserDictionaryWord(rs.getLong(1),rs.getInt(2),rs.getInt(3),rs.getString(4),rs.getString(5));}
    private static void requireScope(int tenantId,int userId){if(tenantId<=0||userId<=0)throw new SecurityException("An authenticated tenant user is required.");}
    private static void requireOwner(int userId,int actorId){if(actorId!=userId)throw new SecurityException("A user may only access their own dictionary.");}
}
