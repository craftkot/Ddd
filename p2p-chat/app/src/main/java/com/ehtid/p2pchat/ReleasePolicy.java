package com.ehtid.p2pchat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only the official EHTID Chat release tag format is eligible for updates. */
public final class ReleasePolicy {
    private static final Pattern TAG=Pattern.compile("^ehtid-p2p-chat-v([0-9]{1,4})\\.([0-9]{1,4})(?:\\.([0-9]{1,4}))?$");
    private static final Pattern VERSION=Pattern.compile("^([0-9]{1,4})\\.([0-9]{1,4})(?:\\.([0-9]{1,4}))?$");
    private ReleasePolicy(){}
    public static int[] parseVersion(String v){
        if(v==null)return null;
        Matcher m=VERSION.matcher(v.trim());
        if(!m.matches())return null;
        return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),
                m.group(3)==null?0:Integer.parseInt(m.group(3))};
    }
    public static String versionFromTag(String tag){
        if(tag==null)return null;
        Matcher m=TAG.matcher(tag);
        if(!m.matches())return null;
        return m.group(1)+"."+m.group(2)+"."+(m.group(3)==null?"0":m.group(3));
    }
    public static boolean newer(String tag,String current){
        int[] target=parseVersion(versionFromTag(tag)),installed=parseVersion(current);
        if(target==null||installed==null)return false;
        for(int i=0;i<3;i++){
            if(target[i]!=installed[i])return target[i]>installed[i];
        }
        return false;
    }
    public static String assetName(String tag){
        if(versionFromTag(tag)==null)return null;
        return "EHTID-P2P-Chat-"+tag.substring("ehtid-p2p-chat-".length())+".apk";
    }
    public static boolean safeDigest(String d){
        return d!=null&&d.matches("sha256:[0-9a-fA-F]{64}");
    }
}
