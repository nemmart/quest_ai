import java.io.*;

import os.*;
import hw.*;
import q.*;

public class QuestMonitor implements Launchable, Runnable {
   public Thread      thread;
   public FSTerminal  terminal;
   public PrintStream out;

   public int[] contents(FSFile file) {
    PageSet pageSet=file.pageSet();
    int     pageCount=file.pageCount();
    int[]   contents=new int[pageCount*1024];

    for(int offset=0;offset<1024*pageCount;offset++) {
     contents[offset]=pageSet.readWord(offset);
    }
    return contents;
   }

   public void monitor(String file) {
    byte[]   buffer=new byte[1];
    FSObject monitor=FS.retrieve(file);
    FSFile   monitorFile;
    int[]    contents, compare;
    int      length, index;
    boolean  found;

    if(monitor==null) {
     out.println("File '" + file + "' not found.");
     return;
    }
    if(!(monitor instanceof FSFile)) {
     out.println("Path '" + file + "' must point to a file");
     return;
    }

    monitorFile=(FSFile)monitor;
    contents=contents(monitorFile);
    length=contents.length;

    out.println("Monitoring '" + file + "' - press a key to exit");
    while(terminal.available()==0) {
     try {
      Thread.sleep(1000);
     }
     catch(InterruptedException exception) {
     }
     compare=contents(monitorFile);
     found=false;
     for(index=0;index<length;index++) {
      if(contents[index]!=compare[index]) {
       found=true;
       out.printf("%04X   %04X %04X\n", index, contents[index], compare[index]);
      }
     }
     contents=compare;
     if(found)
      out.println();
    }
    terminal.read(buffer);
   }

   public int distance(int x, int y, int toX, int toY) {
    return Math.abs(x-toX) + Math.abs(y-toY);
   }

   public void closest(int x, int y, int find) {
    Item item=new Item();
    int  foundX=-1, foundY=-1, distance=-1, count=Item.count();

    for(int index=0;index<count;index++) {
     if(Item.tag(index)==find && Item.valid(index)) {
      item.load(index);
      if(foundX==-1 || distance(x, y, item.x, item.y)<distance) {
       foundX=item.x;
       foundY=item.y;
       distance=distance(x, y, item.x, item.y);
      }
     }
    }
    if(foundX==-1)
     out.println("not found");
    else {
     if(foundY<y)
      out.printf("%d N ", y-foundY);
     else if(foundY>y)
      out.printf("%d S ", foundY-y);
     if(foundX<x)
      out.printf("%d W", x-foundX);
     else if(foundX>x)
      out.printf("%d E", foundX-x);
    }
   }

   public void find(int find) {
    Item[] players=new Item[10];
    Item   item=new Item();
    int    count;

    out.print((char)014);
    while(terminal.available()==0) {
     try {
      Thread.sleep(100);
     }
     catch(InterruptedException exception) {
     }
     out.print((char)010);

     count=Item.count();
     for(int index=0;index<count;index++) {
      int tag=Item.tag(index);

      if(tag>0 && tag<=10) {
       item.load(index);
       out.print(tag + " " + String.format("%04X %04X ", item.x, item.y));
       if(players[tag-1]==null || players[tag-1].x!=item.x || players[tag-1].y!=item.y) {
        if(players[tag-1]==null)
         players[tag-1]=new Item();
        players[tag-1].load(index);
        closest(item.x, item.y, find);
        out.println((char)013);
       }
      }
     }
    }
   }

   public void run() {
    byte[] buffer=new byte[1024];
    int    length;
    String command, split[];

    while(true) {
     out.println("Enter a command, '?' for help.");
     out.print("> ");
     out.flush();
     length=terminal.read(buffer, true);
     try {
      command=new String(buffer, 0, length, "ASCII");
     }
     catch(IOException exception) {
      throw new RuntimeException("ASCII encoding not supported");
     }
     split=command.split(" ");
     if(command.equals("?")) {
      System.out.println("monitor <FILE>   - Monitors shared <FILE> for changes");
     }
     else if(split[0].equals("monitor") && split.length==2) {
      monitor(split[1]);
     }
     else if(split[0].equals("find") && split.length==2) {
      find(Integer.parseInt(split[1]));
     }
     else {
      out.println("Unknown command: " + command);
     }
     out.println();
    }

   }

   public void launch(FSStreamIO terminal) {
    if(terminal instanceof FSTerminal) {
     this.terminal=(FSTerminal)terminal;
     try {
      out=new PrintStream(this.terminal.outputStream, true, "ASCII");
     }
     catch(IOException exception) {
      throw new RuntimeException("Launch failed - " + exception.getMessage());
     }
    }
    else
     throw new RuntimeException("QuestMonitor can only run when connected to a terminal");
    thread=new Thread(this);
    thread.start();
   }
}

