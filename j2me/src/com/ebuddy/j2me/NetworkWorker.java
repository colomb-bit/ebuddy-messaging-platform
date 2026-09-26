package com.ebuddy.j2me;

import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;

interface NetworkListener { void networkResult(int op, String data, Exception error); }

final class NetworkWorker extends Thread {
    static final int LOGIN=1, REGISTER=2, CONTACTS=3, POLL=4, SEND=5;
    private final NetworkListener listener; private final Vector queue=new Vector(); private boolean running=true; private Session session;
    NetworkWorker(NetworkListener l, Session s){listener=l;session=s;start();}
    synchronized void setSession(Session s){session=s;}
    synchronized void request(int op,String path,String body){queue.addElement(new Request(op,path,body));notify();}
    synchronized void stopWorker(){running=false;notify();}
    public void run(){while(running){Request r=null; synchronized(this){while(running&&queue.size()==0)try{wait();}catch(InterruptedException e){} if(!running)break;r=(Request)queue.elementAt(0);queue.removeElementAt(0);} execute(r);}}
    private void execute(Request r){Exception last=null;for(int attempt=0;attempt<4;attempt++){try{String out=send(r);listener.networkResult(r.op,out,null);return;}catch(Exception e){last=e;try{Thread.sleep((attempt+1)*1500L);}catch(InterruptedException x){Thread.currentThread().interrupt();break;}}}listener.networkResult(r.op,null,last);}
    private String send(Request r)throws Exception{HttpConnection h=null;InputStream in=null;OutputStream out=null;try{h=(HttpConnection)Connector.open(AppConfig.API+r.path,Connector.READ_WRITE,true);h.setRequestMethod(r.body==null?HttpConnection.GET:HttpConnection.POST);h.setRequestProperty("User-Agent","Ebuddy-Nokia-C2-05/2.0");h.setRequestProperty("Accept","application/json");h.setRequestProperty("Connection","close");boolean authRoute=r.path.indexOf("/api/auth/")==0;if(!authRoute&&session!=null&&session.token!=null)h.setRequestProperty("Authorization","Bearer "+session.token);if(r.body!=null){h.setRequestProperty("Content-Type","application/json");out=h.openOutputStream();byte[] b=r.body.getBytes("UTF-8");out.write(b);out.flush();}int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);in=h.openInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[256];int n,total=0;while((n=in.read(buf))!=-1&&total<AppConfig.MAX_BODY){b.write(buf,0,n);total+=n;}return new String(b.toByteArray(),"UTF-8");}finally{if(out!=null)try{out.close();}catch(Exception e){}if(in!=null)try{in.close();}catch(Exception e){}if(h!=null)try{h.close();}catch(Exception e){}}}
    private static final class Request{int op;String path;String body;Request(int o,String p,String b){op=o;path=p;body=b;}}
}
