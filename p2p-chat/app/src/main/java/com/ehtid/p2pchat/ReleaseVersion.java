package com.ehtid.p2pchat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict semantic version parser; ignore unrelated GitHub releases. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern TAG=Pattern.compile("^ehtid-p2p-chat-v([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?$");
    private static final Pattern APP=Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)$");
    public final int major,minor,patch;
    private ReleaseVersion(int major,int minor,int patch){
        this.major=major;this.minor=minor;this.patch=patch;
    }
    public static ReleaseVersion fromTag(String tag) {
        if(tag==null)return null;
        return parse(TAG.matcher(tag));
    }
    public static ReleaseVersion fromApp(String version) {
        if(version==null)return null;
        return parse(APP.matcher(version));
    }
    private static ReleaseVersion parse(Matcher matcher){
        if(!matcher.matches())return null;
        try {
            int a=Integer.parseInt(matcher.group(1));
            int b=Integer.parseInt(matcher.group(2));
            int c=matcher.group(3)==null?0:Integer.parseInt(matcher.group(3));
            if(a>100000||b>100000||c>100000)return null;
            return new ReleaseVersion(a,b,c);
        }catch(NumberFormatException e){return null;}
    }
    @Override public int compareTo(ReleaseVersion other){
        int majorOrder=Integer.compare(major,other.major);
        if(majorOrder!=0)return majorOrder;
        int minorOrder=Integer.compare(minor,other.minor);
        if(minorOrder!=0)return minorOrder;
        return Integer.compare(patch,other.patch);
    }
    @Override public String toString(){return major+"."+minor+"."+patch;}
}
