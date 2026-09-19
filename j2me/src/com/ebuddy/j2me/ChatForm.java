package com.ebuddy.j2me;
import javax.microedition.lcdui.*; import java.util.Vector;
public final class ChatForm extends Form implements CommandListener { final EbuddyMidlet app; final Contact contact; final TextField input=new TextField("Message","",240,TextField.ANY); final Command send=new Command("Send",Command.OK,1); final Command back=new Command("Back",Command.BACK,2); boolean busy;
 public ChatForm(EbuddyMidlet a,Contact c){super(c.displayName);app=a;contact=c;append("Connected: "+c.state);append(input);addCommand(send);addCommand(back);setCommandListener(this);}
 void addMessages(Vector v){for(int i=0;i<v.size();i++){ChatMessage m=(ChatMessage)v.elementAt(i);boolean mine=m.from!=null&&m.from.equals(app.session.userId);append((mine?"Me: ":contact.displayName+": ")+m.body);if(m.id!=null&&!mine)app.ack(m.id);}}
 void sent(){busy=false;input.setString("");}
 void fail(String s){busy=false;Alert x=new Alert("Chat",s,null,AlertType.WARNING);x.setTimeout(1800);app.display.setCurrent(x,this);}
 public void commandAction(Command c,Displayable d){if(c==send&&!busy){String body=input.getString();if(body.length()==0)return;ChatMessage m=new ChatMessage();m.id="j2me-"+System.currentTimeMillis();m.to=contact.id;m.body=body;busy=true;app.send(m);}else if(c==back)app.showContacts();}
}
