/*
 * Copyright 1999 by dreamBean Software,
 * All rights reserved.
 */
package chat.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.swing.ListModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SwingUtilities;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import chat.interfaces.Message;
import chat.interfaces.MessageListener;
import chat.interfaces.Topic;
import chat.interfaces.TopicInfo;
import chat.interfaces.TopicServer;
import chat.interfaces.ListenerInfo;


/**
 *   This is the chat client model and controller. This is used
 *   by the GUI, but can also be used as a standalone application
 *   for testing purposes.
 *      
 *   @see ChatGUI
 *   @author Rickard Öberg (rickard@dreambean.com)
 *   @version $Revision:$
 */
public class ChatClient
   extends UnicastRemoteObject
   implements MessageListener
{
   // Constants -----------------------------------------------------
   static final String TOPIC_SERVER = "chat-server";
   static final String REMOTE_SERVER = "129.21.108.223"; //"winterfel.student.rit.edu";
    
   // Attributes ----------------------------------------------------
   TopicServer server;
   
   DefaultComboBoxModel topics;
   DefaultListModel users;
   
   Topic currentTopic;
   TopicInfo currentTopicInfo;
   ListenerInfo info;
   String title;
   
   MessageReceiver messageReceiver;
   
   // Static --------------------------------------------------------
   public static void main(String[] args)
      throws IOException
   {
      // Performance tests
      // This will test the throughput of the chat system
      // Typically you should get about 0 ms/message (i.e. very low)
      // The reason for this is that since the message delivery is 
      // batched there will be a number of messages for each RMI call
   
      // Set security policy and security manager
      // Policy allows everything
      // System.setProperty("java.security.policy",ChatClient.class.getResource("/client.policy").toString());
      // System.setSecurityManager(new SecurityManager());
   
      // Get test parameters
      int clientCount = new Integer(args[0]).intValue();
      int messageCount = new Integer(args[1]).intValue();
      int topicIndex = args.length == 3 ? new Integer(args[2]).intValue()-1 : 0;
      
      // Create test clients and subscribe them to the default topic
      Collection clients = new ArrayList();
      ChatClient client = null;
      // The following lines that get a time stamp of when the code starts logging
      // in users.
      long loginStart = System.currentTimeMillis();
      for (int i = 0; i < clientCount; i++)
      {
         client = new ChatClient();
         client.login("Hello"+i);
         client.subscribe(((TopicInfo)client.getTopics().getElementAt(topicIndex)));
         clients.add(client);
         
         // The following commented lines of code tests where the client gets logged
         // in and immediately sends a message to the server, then the next client
         // gets logged in and subscribes to the given topic.
         
         //Message message = new Message("Hello"+i,"Text","Hello "+i+"!");
         //client.publishMessage(message);
         
         System.out.println("Client "+i+" created");
      }
      long loginStop = System.currentTimeMillis();
      
      System.out.println("Clients created");
      
      // Use the last client to send messages
      long start = System.currentTimeMillis();
      for (int i = 0; i < messageCount; i++)
      {
         Message message = new Message("Hello"+(clientCount-1),"Text","Hello "+i+"!");
         client.publishMessage(message);
         if (i % 100 == 0)
            System.out.println(i+" messages sent");
      }
      long end = System.currentTimeMillis();
      long time = end - start;
      
      System.out.println("Test done");
      
      // The following code block iterates through all of the clients then
      // switches the topic of the client to the the third topic in the topic list.
      // There are time stamps to calculate the response time of switching topics.
      /*Iterator iter1 = clients.iterator();
      long topicStart = System.currentTimeMillis();
      int i = 0;
      while (iter1.hasNext())
      {
    	 client = (ChatClient)iter1.next();
         //client.subscribe(((TopicInfo)client.getTopics().getElementAt(2)));
      	 i++;
      }
      long topicStop = System.currentTimeMillis();
      */
      // Log off test clients
      Iterator iter = clients.iterator();
      long logoutStart = System.currentTimeMillis();
      while (iter.hasNext())
      {
         client = (ChatClient)iter.next();
         client.logout();
      }
      long logoutStop = System.currentTimeMillis();
      
      System.out.println("Clients removed");
      
      // Show results
      System.out.println("Total time:"+time);
      // The following lines just output the response times for login,
      // logout and switching topics.
      //System.out.println("Total login time:"+ (loginStop - loginStart));
      //System.out.println("Total logout time:"+ (logoutStop - logoutStart));
      //System.out.println("Total topic switch time:"+ (topicStop - topicStart));
      System.out.println("Nr of clients:"+clientCount);
      System.out.println("Total nr of messages:"+(messageCount*clientCount));
      System.out.println("Time/message:"+(time/messageCount));
      System.out.println("Time/(message*nr of test clients):"+(time/(messageCount*clientCount)));
      System.out.println("Time/(message*(nr of test clients + 1)):"+(time/(messageCount*(clientCount+1))));
   }
   
   // Constructors --------------------------------------------------
   public ChatClient()
      throws IOException
   {
      getTopicServer();
   }

   // Public --------------------------------------------------------
   public void login(String name)
      throws RemoteException
   {
      info = new ListenerInfo(name);
      
      getTopicServer().addListener(info, this);
   }
   
   public void logout()
      throws RemoteException
   {
      if (currentTopic != null)
         unsubscribe();
         
      getTopicServer().removeListener(info);
      server = null;
   }

   public void subscribe(TopicInfo topicInfo)
      throws RemoteException
   {
      if (currentTopic != null)
         unsubscribe();
      
      currentTopic = server.subscribe(topicInfo, info);
      currentTopicInfo = topicInfo;
      
      // Force user list to be loaded
      getUsers();
   }
   
   public void unsubscribe()
      throws RemoteException
   {
      server.unsubscribe(currentTopicInfo, info);
      currentTopic = null;
      currentTopicInfo = null;
      users = null;
   }
   
   public void publishMessage(Message message)
      throws RemoteException
   {
      currentTopic.publishMessage(message);
   }
   
   public void addMessageReceiver(MessageReceiver mr)
   {
      this.messageReceiver = mr;
   }
   
   public void removeMessageReceiver(MessageReceiver mr)
   {
      if (this.messageReceiver == mr)
         this.messageReceiver = null;
   }
   
   public TopicServer getTopicServer()
      throws RemoteException
   {
      if (server == null)
      {
         try
         {
            //Properties cfg = new Properties();
            //cfg.load(getClass().getResourceAsStream("/jndi.properties"));
            // server = (TopicServer)new InitialContext(cfg).lookup();
            
  /**/     	System.setSecurityManager(new RMISecurityManager());
  /**/     	Registry registry = LocateRegistry.getRegistry(REMOTE_SERVER);
  /**/      server = (TopicServer)registry.lookup(TOPIC_SERVER); 
  
            
         } catch (NotBoundException e)
         {
            throw new ServerException("Could not access topic server", e);
         } catch (IOException e)
         {
            throw new ServerException("Could not load jndi.properties", e);
         }
         
         // Get topic list from server
         topics = new DefaultComboBoxModel();
         Iterator tlist = getTopicServer().getTopicInfos().iterator();
         while(tlist.hasNext())
         {
            topics.addElement(tlist.next());
         }
      }
      
      return server;
   }
   
   public ComboBoxModel getTopics()
      throws RemoteException
   {
      return topics;
   }
   
   public ListModel getUsers()
      throws RemoteException
   {
      // Get list from server
      if (users == null)
      {
         users = new DefaultListModel();
         Iterator tlist = currentTopic.getListenerInfos().iterator();
         while(tlist.hasNext())
         {
            users.addElement(tlist.next());
         }
      }
      
      return users;
   }
   
   public ListenerInfo getClientInfo()
   {
      return info;
   }
   
   // MessageListener implementation --------------------------------
   public synchronized void messagePublished(final Collection messages)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         public void run()
         {
            try
            {
               Iterator mlist = messages.iterator();
               while (mlist.hasNext())
               {
                  messagePublished((Message)mlist.next());
               }
            } catch (Exception e)
            {
               e.printStackTrace();
            }
         }
      });
   }   
   
   public synchronized void messagePublished(Message message)
   {
      if (server == null)
      {
         // Not connected - ignore
         return;
      }
      
      try
      {
         if (message.getSender().equals(Message.SYSTEM))
         {
            // System messages
            if (message.getType().equals(Message.TOPIC_CREATED))
            {
               ((DefaultComboBoxModel)getTopics()).addElement(message.getContent());
            } else if (message.getType().equals(Message.TOPIC_REMOVED))
            {
               ((DefaultComboBoxModel)getTopics()).removeElement(message.getContent());
            } else if (message.getType().equals(Message.USER_JOINED))
            {
               if (currentTopic == null) 
                  return; // Ignore
               
               ((DefaultListModel)getUsers()).addElement(message.getContent());
            } else if (message.getType().equals(Message.USER_LEFT))
            {
               if (currentTopic == null) 
                  return; // Ignore
               
               ((DefaultListModel)getUsers()).removeElement(message.getContent());
            } else
            {
               // Normal message
               addMessage(message);
            } 
         } else
         {
            // Normal message
            addMessage(message);
         }
      } catch (RemoteException e)
      {
         e.printStackTrace();
      }
   }   
   
   // Protected -----------------------------------------------------
   void addMessage(Message message)
   {
      if (messageReceiver != null)
         messageReceiver.handleMessage(message);
   }
    
   void addMessage(Throwable error)
   {
      error.printStackTrace();
      addMessage(new Message(Message.SYSTEM, Message.TEXT, error.toString()));
   }
   
   // Inner classes -------------------------------------------------
   public interface MessageReceiver
   {
      public void handleMessage(Message message);
   }
}