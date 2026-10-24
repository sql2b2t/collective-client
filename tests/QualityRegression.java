import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.setting.*;
import java.util.ArrayList;
import java.util.List;

/** Offline checks of production classes, without booting Minecraft. */
public final class QualityRegression {
    private static int failures;
    private static final class Pulse {}
    private static final class Counter {
        int calls;
        @EventHandler private void tick(Pulse event) { calls++; }
    }
    private enum Mode {
        FIRST, SECOND;
        @Override public String toString() { return this == FIRST ? "Первый" : "Второй"; }
    }
    private static void check(String name, Runnable test) {
        try { test.run(); System.out.println("PASS " + name); }
        catch (Throwable t) { failures++; System.out.println("FAIL " + name + ": " + t); }
    }
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        check("event subscription is idempotent", () -> {
            EventBus bus = new EventBus(); Counter counter = new Counter();
            bus.subscribe(counter); bus.subscribe(counter); bus.post(new Pulse());
            require(counter.calls == 1);
            bus.unsubscribe(counter); bus.post(new Pulse()); require(counter.calls == 1);
        });
        check("concurrent subscription is idempotent", () -> {
            EventBus bus = new EventBus(); Counter counter = new Counter();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 16; i++) threads.add(new Thread(() -> bus.subscribe(counter)));
            threads.forEach(Thread::start);
            for (Thread thread : threads) { try { thread.join(); } catch (InterruptedException e) { throw new AssertionError(e); } }
            bus.post(new Pulse()); require(counter.calls == 1);
        });
        check("legacy localized enum config remains readable", () -> {
            EnumSetting<Mode> mode = new EnumSetting<>("mode", Mode.FIRST);
            require(mode.setByName("Второй")); require(mode.get() == Mode.SECOND);
        });
        check("number rejects non-finite inputs", () -> {
            NumberSetting number = new NumberSetting("range", 3.0, 1.0, 10.0, 0.1);
            number.setValue(Double.NaN); require(number.get() == 3.0);
            number.setValue(Double.POSITIVE_INFINITY); require(number.get() == 3.0);
        });
        check("rounding never escapes bounds", () -> {
            NumberSetting number = new NumberSetting("value", 1.0, 0.05, 1.05, 0.1);
            number.setValue(1.05); require(number.get() <= 1.05);
            number.setValue(0.05); require(number.get() >= 0.05);
        });
        check("visibility compatibility API is functional", () -> {
            boolean[] visible = {false};
            BooleanSetting setting = new BooleanSetting("flag", true).handle(() -> visible[0]);
            require(!setting.isVisible()); visible[0] = true; require(setting.isVisible());
        });
        if (failures != 0) throw new AssertionError(failures + " regression checks failed");
    }
}
