package com.mapr;

import java.util.concurrent.Semaphore;

/*
 * Demonstrate using a shared hash table among minor fragments - one minor fragment builds the hash table,
 * and all the minor fragments use it.
 */

public class SharedMem {

  private static final int MAX_AVAILABLE = 10;

  private class HashTable {
    public StringBuffer data;
    public int count;
    HashTable (String arg) {
      data = new StringBuffer(arg);
      count = 0;
    }
  }

  private final Semaphore available = new Semaphore(1, true);

  private HashTable TheHashTable = new HashTable("Initial string");

  private class minorFragment implements Runnable {

    void doWork() throws InterruptedException {
      Thread.sleep( (int) (Math.random() * 100));
    }

    public void run() {
      try {
        available.acquire();
        if (TheHashTable.count == 0) {
          // I'm the first thread - I build the Hash Table
          doWork(); // doing the build
          int len = TheHashTable.data.length();
          TheHashTable.data.delete(0, len);
          TheHashTable.data.append("THIS HASH TABLE WAS BUILT BY " + Thread.currentThread().getId());
          TheHashTable.count = 1;
          System.out.format("Thread %d built the Hash Table\n", Thread.currentThread().getId());
          available.release();
          // Use the HT as needed
          doWork();
          available.acquire();
          TheHashTable.count--;
          // wait for the others, if any
          while (TheHashTable.count > 0) {
            available.release();
            doWork();
            available.acquire();
          }
          // deallocate the HT
          System.out.format("Thread %d deallocated the Hash Table\n", Thread.currentThread().getId());
          available.release();
          return;
        }
        // a consumer
        TheHashTable.count++;
        available.release();
        // Use the HT
        doWork();
        available.acquire();
        System.out.format("Thread %d finished using the Hash Table -> %s\n", Thread.currentThread().getId(), TheHashTable.data.toString());
        TheHashTable.count--;
        available.release();
      } catch (InterruptedException ie) { /* cleanup */ }
    }
  }

  void startWork() {
    System.out.println("******* STARTING WORK *********");
    for (int i = 0; i < MAX_AVAILABLE; i++ ) {
      new Thread(new minorFragment()).start();
    }
  }

   public static void main(String[] args) {
     SharedMem mySharedMem = new SharedMem();
     mySharedMem.startWork();
   }
}
