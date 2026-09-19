package com.ebuddy.j2me;

import java.util.Vector;

final class AppConfig {
    static final String API = "https://api.example.com";
    static final int CONNECT_TIMEOUT = 20000;
    static final int READ_TIMEOUT = 30000;
    static final int MAX_BODY = 4096;
    private AppConfig() {}
}

final class Contact {
    String id; String username; String displayName; String state;
    Contact(String i, String u, String d, String s) { id=i; username=u; displayName=d; state=s; }
    public String toString() { return displayName + ("online".equals(state) ? " [on]" : " [off]"); }
}

final class ChatMessage {
    String id; String from; String to; String body; String status; long sequence; long created;
    public String toString() { return body; }
}

final class Session {
    String token; long expiresAt; String userId; String username; String displayName; long cursor;
    boolean valid() { return token != null && token.length() > 0 && expiresAt > System.currentTimeMillis()/1000L; }
}

final class Store {
    private static final String RS = "ebuddy";
    private Store() {}
    static void saveSession(Session s) {
        javax.microedition.rms.RecordStore r = null;
        try {
            r = javax.microedition.rms.RecordStore.openRecordStore(RS, true);
            String v = safe(s.token)+"|"+safe(s.userId)+"|"+s.expiresAt+"|"+s.cursor;
            byte[] b = v.getBytes("UTF-8");
            if (r.getNumRecords()==0) r.addRecord(b,0,b.length); else r.setRecord(1,b,0,b.length);
        } catch (Exception ignored) {} finally { if (r != null) try { r.closeRecordStore(); } catch (Exception ignored2) {} }
    }
    static Session loadSession() {
        javax.microedition.rms.RecordStore r=null;
        try { r=javax.microedition.rms.RecordStore.openRecordStore(RS,false); if(r.getNumRecords()==0)return null;
            String v=new String(r.getRecord(1),"UTF-8"); int a=v.indexOf('|'), b=v.indexOf('|',a+1), c=v.indexOf('|',b+1); Session s=new Session(); s.token=v.substring(0,a); s.userId=v.substring(a+1,b); s.expiresAt=Long.parseLong(v.substring(b+1,c)); s.cursor=Long.parseLong(v.substring(c+1)); return s;
        } catch(Exception e){return null;} finally {if(r!=null)try{r.closeRecordStore();}catch(Exception ignored){}}
    }
    static void clear() { try { javax.microedition.rms.RecordStore.deleteRecordStore(RS); } catch(Exception ignored) {} }
    private static String safe(String x){return x==null?"":x.replace('|','_');}
}
