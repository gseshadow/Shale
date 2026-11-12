package com.shale.server.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.AiDcrApplicationStore;
import java.math.BigInteger;
import java.util.*;

/** Dormant C1 service. No endpoint, bean, tenant selection, CIMD document or credential issuance. */
public final class DcrApplicationRegistration {
    private final AiDcrApplicationStore registrar;
    public DcrApplicationRegistration(AiDcrApplicationStore registrar){this.registrar=Objects.requireNonNull(registrar);}
    public AiDcrApplicationStore.Registration register(String name,List<String> redirects,Set<AiScope> scopes,String method,List<RSAKey> publicKeys) {
        if(name==null||name.isBlank()||name.length()>200||name.codePoints().anyMatch(Character::isISOControl)||
                redirects==null||redirects.isEmpty()||redirects.size()>5||new HashSet<>(redirects).size()!=redirects.size()||
                scopes==null||scopes.isEmpty()||publicKeys==null)throw invalid();
        boolean confidential="private_key_jwt".equals(method);
        if(!confidential&&!"none".equals(method)||confidential&&(publicKeys.isEmpty()||publicKeys.size()>2)||!confidential&&!publicKeys.isEmpty())throw invalid();
        redirects.forEach(uri->redirect(uri,confidential));
        var keys=new ArrayList<Map<String,Object>>();var kids=new HashSet<String>();
        try {
            for(var key:publicKeys) {
                if(key==null||key.isPrivate()||!JWSAlgorithm.RS256.equals(key.getAlgorithm())||!KeyUse.SIGNATURE.equals(key.getKeyUse())||
                        key.getKeyID()==null||!key.getKeyID().matches("[\\x21-\\x7e]{1,128}")||!kids.add(key.getKeyID())||
                        key.getKeyOperations()!=null&&!key.getKeyOperations().equals(Set.of(KeyOperation.VERIFY))||
                        key.getX509CertURL()!=null||key.getX509CertChain()!=null)throw invalid();
                var rsa=key.toRSAPublicKey();
                if(rsa.getModulus().bitLength()<2048||rsa.getModulus().bitLength()>4096||!rsa.getPublicExponent().equals(BigInteger.valueOf(65537)))throw invalid();
                byte[] modulus=rsa.getModulus().toByteArray();if(modulus[0]==0)modulus=Arrays.copyOfRange(modulus,1,modulus.length);
                keys.add(Map.of("kid",key.getKeyID(),"modulus",HexFormat.of().formatHex(modulus),"exponent",65537));
            }
        }catch(com.nimbusds.jose.JOSEException invalid){throw invalid();}
        var json=new tools.jackson.databind.ObjectMapper();
        return registrar.register(name,json.writeValueAsString(redirects),json.writeValueAsString(keys),confidential?2:1,AiScope.mask(scopes));
    }
    static void redirect(String value,boolean confidential) {
        var uri=OAuthRedirectPolicy.registered(value,confidential);
        for(String segment:uri.getPath().split("/",-1))if(segment.equals(".")||segment.equals(".."))throw invalid();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Validated DCR application metadata required.");}
}
