package com.ebuddy.j2me;

import java.util.Vector;

final class MiniJson {
    private MiniJson() {}
    static String value(String json, String key) {
        if (json==null || key==null) return null; String needle="\""+key+"\""; int p=json.indexOf(needle); if(p<0)return null; p=json.indexOf(':',p+needle.length()); if(p<0)return null; p++;
        while(p<json.length() && (json.charAt(p)==' '||json.charAt(p)=='\t'))p++; if(p>=json.length())return null;
        if(json.charAt(p)=='"'){StringBuffer b=new StringBuffer();p++;while(p<json.length()){char c=json.charAt(p++);if(c=='"')break;if(c=='\\'&&p<json.length()){char e=json.charAt(p++);if(e=='n')b.append('\n');else if(e=='r')b.append('\r');else b.append(e);}else b.append(c);}return b.toString();}
        int e=p;while(e<json.length()&&json.charAt(e)!=','&&json.charAt(e)!='}'&&json.charAt(e)!=']')e++;return json.substring(p,e).trim();
    }
    static long number(String json,String key,long fallback){try{return Long.parseLong(value(json,key));}catch(Exception e){return fallback;}}
    static boolean bool(String json,String key){return "true".equals(value(json,key));}
    static Vector splitObjects(String json){Vector v=new Vector();if(json==null)return v;int p=0;while(true){int a=json.indexOf('{',p);if(a<0)break;int depth=0;boolean quote=false;int i=a;for(;i<json.length();i++){char c=json.charAt(i);if(c=='"'&&(i==0||json.charAt(i-1)!='\\'))quote=!quote;if(!quote){if(c=='{')depth++;if(c=='}'&&--depth==0){v.addElement(json.substring(a,i+1));p=i+1;break;}}}if(i>=json.length())break;}return v;}
}
