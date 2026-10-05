package io.github.therealkamisama.miguelnetwork.core;

/** A Java subprocess fixture, never part of either mod distribution. */
public final class ManagedProcessFixture {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("exit")) System.exit(17);
        if (args[0].equals("ready")) System.out.println("TEST_READY");
        Thread.sleep(30000);
    }
}
