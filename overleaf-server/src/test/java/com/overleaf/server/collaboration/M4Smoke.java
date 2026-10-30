package com.overleaf.server.collaboration;
import com.overleaf.common.sync.*;
import com.overleaf.server.collaboration.*;
import com.overleaf.server.collaboration.SyncBroadcaster.ClientSession;
import java.util.*;
import java.util.concurrent.*;

public class M4Smoke {
    static class S implements ClientSession {
        final String id; final List<SyncMessage> got = new CopyOnWriteArrayList<>();
        S(String id){this.id=id;}
        public String getUserId(){return id;} public boolean isOpen(){return true;}
        public void send(SyncMessage m){got.add(m);}
    }
    static void check(boolean c, String m){ if(!c){ System.out.println("FAIL: "+m); System.exit(1);} System.out.println("OK: "+m); }
    public static void main(String[] a) throws Exception {
        CollaborationThreadPool tp = new CollaborationThreadPool();
        SyncBroadcaster bc = new SyncBroadcaster(tp);
        DocumentSyncManager mgr = new DocumentSyncManager(tp, bc, new OperationHistory());
        S alice = new S("alice"), bob = new S("bob");
        mgr.handleMessage(alice, SyncMessage.createJoinRoom("r1","alice","Alice"));
        Thread.sleep(200);
        mgr.handleMessage(bob, SyncMessage.createJoinRoom("r1","bob","Bob"));
        Thread.sleep(200);
        check(alice.got.stream().anyMatch(m->m.getMessageType()==MessageType.FULL_SYNC), "alice FULL_SYNC");
        check(bob.got.stream().anyMatch(m->m.getMessageType()==MessageType.FULL_SYNC), "bob FULL_SYNC");
        // JSON round trip with special chars
        CRDTOperation o = CRDTOperation.createInsert(0,"a\"b\\c\nd,{}é","alice",1);
        CRDTOperation r = DocumentSyncManager.operationFromJson(DocumentSyncManager.operationToJson(o));
        check(r.getContent().equals(o.getContent()) && r.getOperationId().equals(o.getOperationId()), "JSON roundtrip");
        // sequential edits
        mgr.handleMessage(alice, SyncMessage.createTextEdit("r1","alice","Alice",
            DocumentSyncManager.operationToJson(CRDTOperation.createInsert(0,"Hello","alice",1))));
        Thread.sleep(200);
        check("Hello".equals(mgr.getDocumentContent("r1")), "insert Hello");
        check(bob.got.stream().anyMatch(m->m.getMessageType()==MessageType.TEXT_EDIT), "bob received edit");
        check(alice.got.stream().anyMatch(m->m.getMessageType()==MessageType.ACK), "alice ACK");
        // concurrent edits from same base (lamport 1): alice X@2, bob Y@4
        mgr.handleMessage(alice, SyncMessage.createTextEdit("r1","alice","Alice",
            DocumentSyncManager.operationToJson(CRDTOperation.createInsert(2,"X","alice",2))));
        Thread.sleep(200);
        mgr.handleMessage(bob, SyncMessage.createTextEdit("r1","bob","Bob",
            DocumentSyncManager.operationToJson(CRDTOperation.createInsert(4,"Y","bob",2))));
        Thread.sleep(200);
        System.out.println("content=" + mgr.getDocumentContent("r1"));
        check("HeXllYo".equals(mgr.getDocumentContent("r1")), "OT converge HeXllYo");
        // stress: many threads, many edits, no loss
        int N=200; CountDownLatch l=new CountDownLatch(N);
        for(int i=0;i<N;i++){ final int k=i;
            new Thread(()->{ mgr.handleMessage(k%2==0?alice:bob, SyncMessage.createTextEdit("r1", k%2==0?"alice":"bob","n",
              DocumentSyncManager.operationToJson(CRDTOperation.createInsert(0,"z",k%2==0?"alice":"bob",1000+k)))); l.countDown(); }).start(); }
        l.await(); Thread.sleep(1000);
        long z = mgr.getDocumentContent("r1").chars().filter(c->c=='z').count();
        check(z==N, "stress no lost edits: "+z+"/"+N);
        mgr.handleMessage(bob, SyncMessage.createLeaveRoom("r1","bob","Bob"));
        Thread.sleep(200);
        check(mgr.getCollaboratorCount("r1")==1, "leave room");
        tp.shutdown();
        System.out.println("ALL PASSED");
    }
}
