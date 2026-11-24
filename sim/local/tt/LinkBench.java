package local.tt;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Headless bench for the MMI link: the head unit's real receiver (TeleLink) against a sender that
 * follows the board's contract (OpenHaldex v1.1.19 mmi_link.cpp): while unlinked it probes one port
 * per 250 ms through 49153..49162, starting at the last port that answered; an ack's source port
 * is kept; 20 Hz telemetry while linked; no ack for 3 s -> back to probing. Other programs
 * (CarPlay) are played by sockets holding random ports.
 *
 *   java -cp out local.tt.LinkBench [seed]
 *
 * Scenarios, each with a pass/fail line:
 *   1 random ports taken before the receiver starts
 *   2 receiver restarts and its old port is taken meanwhile (CarPlay connecting)
 *   3 every port taken, then one freed
 *   4 sender stops: the layout shows no link and no stale values
 *   5 sender restarts (board reboot): back within a sweep
 */
public final class LinkBench {
    static final int FIRST = 49153;
    static final int LAST = 49162;

    // sender (board) state
    private DatagramSocket tx;
    private int port = 49160;
    private int goodPort = 49160;
    private long lastAck;
    private long lastProbe;
    private int seq;
    private long acks;
    private volatile boolean sending;
    private Thread txThread;

    private final List<DatagramSocket> blockers = new ArrayList<DatagramSocket>();
    private final Random rnd;
    private int fails;

    private LinkBench(long seed) {
        rnd = new Random(seed);
    }

    public static void main(String[] a) throws Exception {
        long seed = a.length > 0 ? Long.parseLong(a[0]) : System.currentTimeMillis();
        System.out.println("LinkBench seed " + seed);
        LinkBench b = new LinkBench(seed);
        b.run();
        System.out.println(b.fails == 0 ? "ALL PASS" : b.fails + " FAILED");
        System.exit(b.fails == 0 ? 0 : 1);
    }

    private void run() throws Exception {
        // 1 random ports taken before the receiver starts
        List<Integer> ports = new ArrayList<Integer>();
        for (int p = FIRST; p <= LAST; p++) {
            ports.add(p);
        }
        Collections.shuffle(ports, rnd);
        int taken = 1 + rnd.nextInt(8);
        for (int i = 0; i < taken; i++) {
            block(ports.get(i));
        }
        System.out.println("1: " + taken + " ports taken: " + blockedList());
        TeleLink.start(0);
        TeleLink.want(true);
        startSender();
        long t1 = waitLinked(8000);
        check("1 locks with " + taken + " ports taken", t1 >= 0, t1 + " ms, receiver on " + boundPort());
        check("1 acks keep coming", acksOver(2000) > 30, "");

        // 2 receiver restart, its port taken meanwhile
        int old = boundPort();
        TeleLink.want(false);
        Thread.sleep(800); // the rx loop notices within 500 ms and closes the socket
        block(old);
        TeleLink.want(true);
        long t0 = System.currentTimeMillis();
        long t2 = waitLinkedAfter(t0, 12000);
        check("2 recovers after restart with old port " + old + " taken", t2 >= 0,
            t2 + " ms, receiver now on " + boundPort());

        // 3 every port taken, then one freed
        TeleLink.want(false);
        Thread.sleep(800);
        unblockAll();
        for (int p = FIRST; p <= LAST; p++) {
            block(p);
        }
        TeleLink.want(true);
        Thread.sleep(3000);
        boolean noneWhileFull = !TeleLink.boundOpen();
        int freed = FIRST + rnd.nextInt(LAST - FIRST + 1);
        unblock(freed);
        t0 = System.currentTimeMillis();
        long t3 = waitLinkedAfter(t0, 15000);
        check("3 nothing bound while every port is taken", noneWhileFull, "");
        check("3 recovers when port " + freed + " frees", t3 >= 0, t3 + " ms, receiver on " + boundPort());

        // 4 sender stops: no link, nothing stale shown
        stopSender();
        Thread.sleep(2600);
        TeleLayout l = new TeleLayout();
        TeleLink.snapshot(l, System.currentTimeMillis());
        check("4 link drops 2 s after the last datagram", !l.link, "");
        check("4 no channel still counts as fresh", !l.has[TeleLayout.CH_RPM], "");

        // 5 board reboot: new socket, starts probing at its default
        port = 49160;
        goodPort = 49160;
        lastAck = 0;
        startSender();
        t0 = System.currentTimeMillis();
        long t5 = waitLinkedAfter(t0, 6000);
        check("5 sender back after a reboot", t5 >= 0, t5 + " ms");
        stopSender();
        TeleLink.want(false);
        unblockAll();
        System.out.println(TeleLink.info());
    }

    // ---------------------------------------------------------------- sender (board contract)

    private void startSender() throws Exception {
        tx = new DatagramSocket();
        tx.setSoTimeout(20);
        sending = true;
        txThread = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "bench-tx");
        txThread.setDaemon(true);
        txThread.start();
    }

    private void stopSender() throws Exception {
        sending = false;
        txThread.join(1000);
        tx.close();
    }

    private void loop() {
        byte[] in = new byte[64];
        long lastSend = 0;
        while (sending) {
            long now = System.currentTimeMillis();
            boolean linked = lastAck != 0 && now - lastAck < 3000;
            try {
                if (linked) {
                    if (now - lastSend >= 50) {
                        send(true);
                        lastSend = now;
                    }
                } else if (now - lastProbe >= 250) {
                    port = lastProbe == 0 ? goodPort : (port >= LAST ? FIRST : port + 1);
                    send(false);
                    lastProbe = now;
                }
                DatagramPacket p = new DatagramPacket(in, in.length);
                tx.receive(p);
                if (p.getLength() >= 20 && in[0] == 'T' && in[1] == 'T' && in[2] == 'A' && in[3] == '1') {
                    int from = p.getPort();
                    if (from >= FIRST && from <= LAST) {
                        port = from;
                        goodPort = from;
                    }
                    lastAck = System.currentTimeMillis();
                    acks++;
                }
            } catch (SocketTimeoutException e) {
                // nothing this round
            } catch (Exception e) {
                // the board keeps going whatever the socket does
            }
            if (!linked && lastAck != 0 && System.currentTimeMillis() - lastAck >= 3000) {
                lastProbe = 1; // next probe moves on from the current port
            }
        }
    }

    /** TTG1 with rpm and boost; a probe is the same datagram (the receiver acks every TTG1). */
    private void send(boolean data) throws Exception {
        byte[] b = new byte[12 + 2 * 6];
        b[0] = 'T';
        b[1] = 'T';
        b[2] = 'G';
        b[3] = '1';
        // key 0 at 4..7
        seq = (seq + 1) & 0xFFFF;
        b[8] = (byte) (seq >> 8);
        b[9] = (byte) seq;
        b[10] = 0;
        b[11] = 2;
        put(b, 12, TeleLayout.CH_RPM, 4000000);
        put(b, 18, TeleLayout.CH_BOOST, 80000);
        tx.send(new DatagramPacket(b, b.length, InetAddress.getByName("127.0.0.1"), port));
    }

    private static void put(byte[] b, int o, int id, int v) {
        b[o] = (byte) (id >> 8);
        b[o + 1] = (byte) id;
        b[o + 2] = (byte) (v >> 24);
        b[o + 3] = (byte) (v >> 16);
        b[o + 4] = (byte) (v >> 8);
        b[o + 5] = (byte) v;
    }

    // ---------------------------------------------------------------- helpers

    private long waitLinked(long max) throws Exception {
        return waitLinkedAfter(System.currentTimeMillis(), max);
    }

    /** ms from t0 until three acks arrived after t0, or -1. */
    private long waitLinkedAfter(long t0, long max) throws Exception {
        long a0 = acks;
        while (System.currentTimeMillis() - t0 < max) {
            if (acks - a0 >= 3 && lastAck > t0) {
                return System.currentTimeMillis() - t0;
            }
            Thread.sleep(20);
        }
        return -1;
    }

    private long acksOver(long ms) throws Exception {
        long a0 = acks;
        Thread.sleep(ms);
        return acks - a0;
    }

    private void block(int p) {
        try {
            blockers.add(new DatagramSocket(p));
        } catch (Exception e) {
            // taken already (the receiver holds it): fine for the test
        }
    }

    private void unblock(int p) {
        for (int i = 0; i < blockers.size(); i++) {
            if (blockers.get(i).getLocalPort() == p) {
                blockers.remove(i).close();
                return;
            }
        }
    }

    private void unblockAll() {
        for (DatagramSocket s : blockers) {
            s.close();
        }
        blockers.clear();
    }

    private String blockedList() {
        StringBuilder sb = new StringBuilder();
        for (DatagramSocket s : blockers) {
            sb.append(s.getLocalPort()).append(' ');
        }
        return sb.toString().trim();
    }

    private static int boundPort() {
        return TeleLink.boundPortNow();
    }

    private void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + what + (detail.length() > 0 ? " (" + detail + ")" : ""));
        if (!ok) {
            fails++;
        }
    }
}
