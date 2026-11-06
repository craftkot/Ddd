package com.ehtid.p2pchat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict semantic version policy for public EHTID GitHub Releases. */
public final class UpdateVersion implements Comparable<UpdateVersion> {
    private static final Pattern SEMVER =
            Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");
    private static final Pattern TAG =
            Pattern.compile("^ehtid-p2p-chat-v(\\d+\\.\\d+(?:\\.\\d+)?)$");
    public final int major,minor,patch;

    private UpdateVersion(int major,int minor,int patch) {
        this.major=major;this.minor=minor;this.patch=patch;
    }

    public static UpdateVersion parse(String text) {
        if(text==null)return null;
        Matcher m=SEMVER.matcher(text);
        if(!m.matches())return null;
        try {
            int major=Integer.parseInt(m.group(1)),minor=Integer.parseInt(m.group(2));
            int patch=m.group(3)==null?0:Integer.parseInt(m.group(3));
            if(major>9999||minor>9999||patch>9999)return null;
            return new UpdateVersion(major,minor,patch);
        }catch(NumberFormatException ignored){return null;}
    }

    public static UpdateVersion fromReleaseTag(String tag) {
        if(tag==null)return null;
        Matcher m=TAG.matcher(tag);
        return m.matches()?parse(m.group(1)):null;
    }

    public static String expectedApkName(String tag) {
        if(fromReleaseTag(tag)==null)return "";
        return "EHTID-P2P-Chat-"+tag.substring("ehtid-p2p-chat-".length())+".apk";
    }

    public int compareTo(UpdateVersion other) {
        int a=Integer.compare(major,other.major);
        if(a!=0)return a;
        int b=Integer.compare(minor,other.minor);
        return b!=0?b:Integer.compare(patch,other.patch);
    }

    @Override public String toString() {return major+"."+minor+"."+patch;}
}
