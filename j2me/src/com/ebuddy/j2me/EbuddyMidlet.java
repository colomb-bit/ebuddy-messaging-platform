package com.ebuddy.j2me;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import java.util.Vector;

public final class EbuddyMidlet extends MIDlet implements NetworkListener {
    Display display; NetworkWorker net; Session session; LoginForm login; RegisterForm register; ContactList contacts; ChatForm chat; boolean paused;
    public void startApp(){if(display==null){display=Display.getDisplay(this);session=Store.loadSession();net=new NetworkWorker(this,session);if(session!=null&&session.valid())showContacts();else showLogin();}else paused=false;}
    public void pauseApp(){paused=true;}
    public void destroyApp(boolean unconditional){if(net!=null)net.stopWorker();}
    void showLogin(){login=new LoginForm(this);display.setCurrent(login);}
    void showRegister(){register=new RegisterForm(this);display.setCurrent(register);}
    void showContacts(){contacts=new ContactList(this);display.setCurrent(contacts);contacts.load();}
    void openChat(Contact c){if(c!=null){chat=new ChatForm(this,c);display.setCurrent(chat);}}
    void logout(){Store.clear();session=null;if(net!=null)net.stopWorker();net=new NetworkWorker(this,null);showLogin();}
    void login(String u,String p){login.setBusy(true);net.request(NetworkWorker.LOGIN,"/api/auth/login",jsonLogin(u,p,"j2me"));}
    void register(String u,String p,String name){register.setBusy(true);net.request(NetworkWorker.REGISTER,"/api/auth/register","{\"username\":\""+MiniJsonEscape.clean(u)+"\",\"password\":\""+MiniJsonEscape.clean(p)+"\",\"displayName\":\""+MiniJsonEscape.clean(name)+"\"}");}
    private String jsonLogin(String u,String p,String t){return "{\"username\":\""+MiniJsonEscape.clean(u)+"\",\"password\":\""+MiniJsonEscape.clean(p)+"\",\"clientType\":\""+t+"\",\"deviceLabel\":\"Nokia C2-05\"}";}
    void send(ChatMessage m){net.request(NetworkWorker.SEND,"/api/j2me/send","{\"to_user_id\":\""+m.to+"\",\"client_message_id\":\""+m.id+"\",\"body\":\""+MiniJsonEscape.clean(m.body)+"\"}");}
    void poll(){if(session!=null&&session.valid())net.request(NetworkWorker.POLL,"/api/j2me/poll?cursor="+session.cursor+"&wait=25&limit=20",null);}
    void ack(String id){net.request(NetworkWorker.SEND,"/api/messages/"+id+"/receipt","{\"state\":\"delivered\"}");}
    public void networkResult(final int op,final String data,final Exception error){if(display==null)return;display.callSerially(new Runnable(){public void run(){handleNetworkResult(op,data,error);}});}
    private void handleNetworkResult(int op,String data,Exception error){if(error!=null){if(op==NetworkWorker.LOGIN&&login!=null)login.fail("Network unavailable");else if(op==NetworkWorker.REGISTER&&register!=null)register.fail("Network unavailable");else if(chat!=null)chat.fail("Retry later");return;}if(op==NetworkWorker.LOGIN){session=new Session();session.token=MiniJson.value(data,"token");session.expiresAt=MiniJson.number(data,"expiresAt",0);session.userId=MiniJson.value(data,"id");if(session.userId==null){String user=MiniJson.value(data,"user");if(user!=null)session.userId=MiniJson.value(user,"id");}if(session.token==null){login.fail("Invalid login response");return;}net.setSession(session);Store.saveSession(session);showContacts();}else if(op==NetworkWorker.REGISTER){showLogin();}else if(op==NetworkWorker.CONTACTS){if(contacts!=null)contacts.setItems(parseContacts(data));}else if(op==NetworkWorker.POLL){Vector v=parseMessages(data);if(chat!=null)chat.addMessages(v);session.cursor=MiniJson.number(data,"nextCursor",session.cursor);Store.saveSession(session);if(!paused)poll();}else if(op==NetworkWorker.SEND){if(chat!=null)chat.sent();}}
    private Vector parseContacts(String data){Vector out=new Vector();Vector a=MiniJson.splitObjects(data);for(int i=0;i<a.size();i++){String x=(String)a.elementAt(i);String u=MiniJson.value(x,"username");String n=MiniJson.value(x,"displayName");String id=MiniJson.value(x,"id");String s=MiniJson.value(x,"state");if(id!=null)out.addElement(new Contact(id,u,n==null?u:n,s));}return out;}
    private Vector parseMessages(String data){Vector out=new Vector();Vector a=MiniJson.splitObjects(data);for(int i=0;i<a.size();i++){String x=(String)a.elementAt(i);ChatMessage m=new ChatMessage();m.id=MiniJson.value(x,"id");m.from=MiniJson.value(x,"fromUserId");m.to=MiniJson.value(x,"toUserId");m.body=MiniJson.value(x,"body");m.status=MiniJson.value(x,"status");m.sequence=MiniJson.number(x,"sequence",0);if(m.body!=null)out.addElement(m);}return out;}
}
final class MiniJsonEscape{static String clean(String s){if(s==null)return "";StringBuffer b=new StringBuffer();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'||c=='"')b.append('\\');if(c=='\n')b.append("\\n");else if(c=='\r')b.append("\\r");else b.append(c);}return b.toString();}}
