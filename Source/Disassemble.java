import java.io.*;
import java.net.*;

import hw.*;
import os.*;
import debug.*;

public class Disassemble {

   static public int checkForLabels(SymbolTable symbols, int start, int stop) {
    int index;

    for(index=start;index<stop;index++)
     if(symbols.nameForAddress(index)!=null)
      break;
    return index-start;
   }

   static public void dumpMemory(SymbolTable symbols, Memory memory, int start, int stop) {
     int current=start, base;

     System.out.printf("\nmemory: 0x%08X 0x%08X\n", start, stop);
     while(current<stop) {
       if(symbols.addressToName.get(current)!=null) {
        if(current!=start)
          System.out.println();
        System.out.printf("# ----------------------------------------------------------------\n");
        System.out.printf("# %s\n", symbols.addressToName.get(current));
        System.out.printf("# ----------------------------------------------------------------\n");
       }
       base=current;
       while(current==base || (current<stop && symbols.addressToName.get(current)==null))
         current++;
       MemoryDumper.dump(memory, base, current);
     }
   }

   static public void disassemble(SymbolTable symbols, Memory memory, int start, int stop) {
    int         index, count, dumpStart, dumpStop, current, nextDecode, opcode;
    String      symbol;
    Instruction instruction;
    String      name;

    nextDecode=start;
    index=start;
    while(index<stop) {
     dumpStart=index;
     dumpStop=index+24;
     if(index>start+8)
      dumpStart=index-8;

     count=MemoryDumper.count(memory, dumpStart, stop);
     if(count>=48)
      count=checkForLabels(symbols, dumpStart, dumpStart+count);
     count=count-count%8;

     if(count>=48) {
      dumpStop+=count;
      index=dumpStart+count;
     }
     if(dumpStop>stop)
      dumpStop=stop;

     System.out.println();
     MemoryDumper.dump(memory, dumpStart, dumpStop);
     System.out.println();

     for(current=index;current<index+16 && current<stop;current++) {
      symbol=symbols.nameForAddress(current);
      if(symbol!=null) {
       System.out.println("----------------------------------------------------------------");
       System.out.println(symbol);
       System.out.println("----------------------------------------------------------------");
      }
      System.out.printf("%06x %04x ", current, memory.readWord(current));
      if(current>=nextDecode) {
       opcode=memory.readWord(current);
       if(opcode==0xc619 && memory.readWord(current+1)==0x8006) {
        System.out.printf("SYSCALL 0%o", memory.readWord(current+2));
        nextDecode=current+3;
       }
       else {
        instruction=Instruction.decode(false, opcode);
        if(instruction==null) {
         System.out.print("undef");
         nextDecode=current+1;
        }
        else {
         name=instruction.name;
         if(name==null)
          throw new RuntimeException("Unnamed instruction for opcode " + String.format("%04X", opcode));
         if(name!=null && name.endsWith("*"))
          name=name.substring(0, name.length()-1);
         if(instruction.instructionFormat==null) {
          System.out.print(name);
          nextDecode=current+1;
         }
         else {
          System.out.print(Disassembler.disassemble(memory, current, name, opcode, instruction.instructionFormat));
          nextDecode=current+Disassembler.wordLength(instruction.instructionFormat);
         }
        }
       }
      }
      System.out.println();
     }
     index=index+16;
    }
   }

   static public String findSymbol(String description, SymbolTable symbols) {
    int    bracket=description.indexOf("[0x"), paren=description.indexOf("(0x");
    int    start=(paren!=-1) ? paren : bracket, addr;
    String symbol;

    if(description.startsWith("XCALL") || description.startsWith("LCALL") ||
       description.startsWith("XJSR") || description.startsWith("LJSR") ||
       description.startsWith("XJMP") || description.startsWith("LJMP")) {
     if(start==-1)
      return " # addr not found";
     addr=Integer.parseInt(description.substring(start+3, start+11), 16);
     symbol=symbols.addressToName.get(addr);
     if(symbol==null && description.startsWith("XJMP") || description.startsWith("LJMP"))
      return "";
     if(symbol==null)
      return " # symbol not found";
     else
      return " # " + symbol;
    }
    return "";
   }

   static public void disassembleCompact(SymbolTable symbols, Memory memory, int start, int stop, boolean showHex) {
    int         count, current, opcode, length;
    String      symbol, description;
    Instruction instruction;
    String      name;

    System.out.printf("\ndisassemble: 0x%08X 0x%08X\n", start, stop);
    current=start;
    while(current<stop) {
      symbol=symbols.nameForAddress(current);
      if(symbol!=null) {
       System.out.printf("# ----------------------------------------------------------------\n");
       System.out.printf("# %s\n", symbol);
       System.out.printf("# ----------------------------------------------------------------\n");
      }
      System.out.printf("%06x ", current, memory.readWord(current));
      if(showHex)
        System.out.printf("%04x ", memory.readWord(current));
      opcode=memory.readWord(current);
      if(opcode==0xc619 && memory.readWord(current+1)==0x8006) {
       if(showHex)
         System.out.printf("8006 %04x ", memory.readWord(current+2));
       System.out.printf("SYSCALL 0%o\n", memory.readWord(current+2));
       current=current+3;
      }
      else {
       instruction=Instruction.decode(false, opcode);
       if(instruction==null) {
        System.out.println("undef");
        current++;
       }
       else {
        name=instruction.name;
        if(name==null)
         throw new RuntimeException("Unnamed instruction for opcode " + String.format("%04X", opcode));
        if(name!=null && name.endsWith("*"))
         name=name.substring(0, name.length()-1);
        if(instruction.instructionFormat==null) {
         System.out.println(name);
         current++;
        }
        else {
         length=Disassembler.wordLength(instruction.instructionFormat);
         if(showHex) {
           for(int i=1;i<length;i++)
            System.out.printf("%04x ", memory.readWord(current+i));
         }
         description=Disassembler.disassemble(memory, current, name, opcode, instruction.instructionFormat);
         symbol=findSymbol(description, symbols);
         System.out.printf("%s;%s\n", Disassembler.disassemble(memory, current, name, opcode, instruction.instructionFormat), symbol);
         current+=length;
        }
       }
      }
    }
   }

   static public void main(String[] args) throws Exception {
    FSTerminal  terminal;
    String      file;
    FSChannel   channel;
    PageSet     filePages;
    OSProcess   process;
    SymbolTable symbols;
    Memory      fileMemory, processMemory;

    if(args.length!=2 && args.length!=3) {
     System.err.println("Usage: java Disassemble <dir> <PR file> [<address file>]");
     System.exit(1);
    }

    FS.initializeWithPath(args[0]);

    file=args[1].toUpperCase();
    if(file.endsWith(".PR"))
     file=file.substring(0, file.length()-3);

    channel=FSChannel.openForPagedIO(":" + file + ".PR", true);
    filePages=channel.pageSet();

    process=new OSProcess(":", file);
    symbols=process.symbols;
    processMemory=process.memory;

    int sharedStartPage=filePages.readWord(0x10F);
    int sharedPageCount=filePages.readWord(0x113);
    int fileSharedOffset=filePages.readWord(0x11A);
    int programStartAddress=filePages.readWide(0x17C);
    int SEGMENT_BASE=OSProcess.SEGMENT_BASE*1024;

    System.out.printf("Program start address: 0x%08x\n", programStartAddress);

    System.out.println("------------------------------------- HEADER ------------------------------------");
    System.out.println();
    MemoryDumper.dump(filePages, 0, 0x2000);
    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println();

    if(args.length==2) {
      System.out.println("----------------------------------- LOW MEMORY ----------------------------------");
      disassemble(symbols, processMemory, SEGMENT_BASE, SEGMENT_BASE+(fileSharedOffset-8)*1024);

//    mapFile(channel, SEGMENT_BASE, fileSharedOffset-8, 8, false, true, true);
//    mapFile(channel, sharedStartPage+SEGMENT_BASE, sharedPageCount, fileSharedOffset, true, false, false);

      System.out.println();
      System.out.println();
      System.out.println();
      System.out.println();
      System.out.println("---------------------------------- HIGH  MEMORY ---------------------------------");
      disassemble(symbols, processMemory, SEGMENT_BASE+sharedStartPage*1024, SEGMENT_BASE+(sharedStartPage+sharedPageCount)*1024);
    }
    else {
      BufferedReader reader=new BufferedReader(new FileReader(args[2]));
      String         line, split[];
      int            startAddress, stopAddress;

      while(true) {
        line=reader.readLine();
        if(line==null)
          break;
        split=line.trim().split(" ");
        startAddress=Integer.parseInt(split[1], 16);
        stopAddress=Integer.parseInt(split[2], 16);

        if(split[0].equals("code"))
          disassembleCompact(symbols, processMemory, startAddress, stopAddress, false);
        else if(split[0].equals("mem"))
          dumpMemory(symbols, processMemory, startAddress, stopAddress);
        else
          throw new RuntimeException("Expected code or const, found '" + split[0] + "'");
      }
    }
   }
}

