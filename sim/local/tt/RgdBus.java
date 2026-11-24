package local.tt;

/** PC stand-in for the head unit's log (tt-zoom's RgdBus writes /tmp/tt-rgd.log): prints instead. */
final class RgdBus {
    private RgdBus() {
    }

    static void note(String s) {
        System.out.println(s);
    }
}
