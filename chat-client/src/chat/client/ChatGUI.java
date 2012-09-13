/*
 * Copyright 1999 by dreamBean Software,
 * All rights reserved.
 */
package chat.client;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Properties;
import java.util.HashMap;

import javax.swing.JApplet;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.metal.MetalLookAndFeel;

import chat.interfaces.Message;
import chat.interfaces.MessageListener;
import chat.interfaces.Topic;
import chat.interfaces.TopicInfo;
import chat.interfaces.TopicServer;
import chat.interfaces.ListenerInfo;

/**
 *   This is the chat client applet GUI. This allow the user
 *   to interact with the server through a user interface. 
 *      
 *   @see ChatClient
 *   @author Rickard Öberg (rickard@dreambean.com)
 *   @version $Revision:$
 */
public class ChatGUI
   extends JApplet
   implements ChatClient.MessageReceiver, ActionListener
{
   // Constants -----------------------------------------------------
   // This message is sent when user starts typing
   // It allows GUI to denote that user is currently typing a message
   static final String TYPING = "IsTyping"; 
    
   // Attributes ----------------------------------------------------
   CardLayout mainLayout = new CardLayout();
   JPanel mainPanel = new JPanel(mainLayout);

   JPanel loginPanel = new JPanel(new BorderLayout());
   JTextField loginField = new JTextField();
   JLabel loginLabel = new JLabel("Welcome to the chat!", JLabel.CENTER);
   
   TitledBorder title;
   JTextArea messages = new JTextArea();
   JScrollPane scroll = new JScrollPane(messages);
   JTextField input = new JTextField();
   JComboBox topics = new JComboBox();
   JList users = new JList();
   JScrollPane userScroll = new JScrollPane(users);
   
   ChatClient client;
   
   HashMap isTyping = new HashMap();
   
   // Constructors --------------------------------------------------
   public ChatGUI()
      throws RemoteException
   {
      // Setup GUI
      getContentPane().add("Center", mainPanel);
      mainPanel.add("Login", loginPanel);
      
      loginPanel.add("North", loginField);
      loginPanel.add("Center", loginLabel);
      loginPanel.setBackground(loginField.getBackground());
      loginField.setBorder(BorderFactory.createTitledBorder("Enter your name"));
      
      title = BorderFactory.createTitledBorder("Welcome to the chat!");
      title.setTitleJustification(TitledBorder.CENTER);
      scroll.setBorder(title);
      scroll.setBackground(messages.getBackground());
      messages.setEnabled(false);
      messages.setDisabledTextColor(getContentPane().getForeground());
      input.setBorder(BorderFactory.createTitledBorder("Enter message"));
      userScroll.setBorder(BorderFactory.createTitledBorder("Users"));
      userScroll.setBackground(users.getBackground());
      
      JPanel chatGui = new JPanel(new BorderLayout());
      chatGui.add("North", topics);
      chatGui.add("Center",scroll);
      chatGui.add("South",input);
      chatGui.add("East",userScroll);
      
      mainPanel.add("Chat", chatGui);
      
      loginField.addActionListener(this);
      input.addActionListener(this);
      topics.addActionListener(this);
      
      // IsTyping renderer
      final ListCellRenderer renderer = users.getCellRenderer();
      users.setCellRenderer(new ListCellRenderer()
      {
         public Component getListCellRendererComponent(JList list,
                                                         Object value,
                                                         int index,
                                                         boolean isSelected,
                                                         boolean cellHasFocus)
         {
            Component c = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (isTyping.get(value.toString()) != null)
               c.setBackground(MetalLookAndFeel.getControlShadow());
            
            return c;
         }
      });
      
      // IsTyping message publisher
      input.addKeyListener(new KeyAdapter()
      {
         public void keyPressed(KeyEvent e)
         {
            if (input.getText().equals(""))
            {
               sendMessage(new Message(client.getClientInfo().getName(), TYPING, null));
            }
         }
      });
   }

   // MessageReceiver implementation --------------------------------
   public void handleMessage(Message message)
   {
      if (message.getType().equals(Message.TEXT))
      {
         String msg;
         if (message.getSender().equals(Message.SYSTEM))
         {
            msg = (String)message.getContent();
            users.repaint();
         } else
         {
            // Normal message
            msg = message.getSender()+": "+message.getContent();
   
            // Reset typing
            isTyping.remove(message.getSender());
            users.repaint();
         }
         messages.append(msg+"\n");
         messages.setCaretPosition(messages.getText().length());
      } else if (message.getType().equals(Message.GREETING))
      {
         title.setTitle(message.getContent().toString());
         scroll.repaint();
      } else if (message.getType().equals(TYPING))
      {
         // Mark that this user is typing
         isTyping.put(message.getSender(), message.getSender());
   
         // Repaint user list
         users.repaint();
      }
   }
   
   // ActionListener implementation ---------------------------------
   public void actionPerformed(ActionEvent e)
   {
      if (e.getSource() == loginField) // Login name entered
      {
         // Register with server
         try
         {
            client = new ChatClient();
            client.addMessageReceiver(this);
            
            client.login(loginField.getText());
            
            topics.setModel(client.getTopics());
            
            mainLayout.show(mainPanel, "Chat");
         } catch (IOException exc)
         {
            error(exc);
         }
         
         // Reset input field
         loginField.setText("");
      } else if (e.getSource() == input) // Send chat message
      {
         // Send message
         final String msg = input.getText();
         // Use a new thread to minimize lag
         SwingUtilities.invokeLater(new Runnable()
         {
            public void run()
            {
               sendMessage(new Message(client.getClientInfo().getName(), Message.TEXT, msg));
            }
         });
         
         // Reset input field
         input.setText("");
      } else if (e.getSource() == topics)
      {
         try
         {
            client.subscribe(((TopicInfo)topics.getSelectedItem()));
            users.setModel(client.getUsers());
            
            handleMessage(new Message(Message.SYSTEM, Message.TEXT, ((TopicInfo)topics.getSelectedItem()).getDescription()));
         } catch (IOException exc)
         {
            error(exc);
         }
      }

   }

   
   // Applet overrides ----------------------------------------------
   public void destroy()
   {
      try
      {
         if (client != null)
            client.logout();
      } catch (IOException exc)
      {
         error(exc);
      }
   }

   // Protected -----------------------------------------------------
   protected void sendMessage(Message message)
   {
      try
      {
         client.publishMessage(message);
      } catch (IOException exc)
      {
         error(exc);
      }
   }
    
   protected void error(Exception e)
   {
      client = null;
      loginLabel.setText(e.getMessage());
      e.printStackTrace();
      mainLayout.show(mainPanel, "Login");
      
      input.setText("");
   }
}