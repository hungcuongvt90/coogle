package io.github.zopulus.coogle;
import android.app.Application;
import com.google.android.gms.tapandpay.firstparty.CardInfo;
import java.lang.reflect.Method;
import java.util.List;

public final class WalletPreviewCandidatesTest {
    public static void main(String[] args) throws Exception {
        Method getter = RenamedModel.class.getDeclaredMethod("renamedState");
        Method callback = RenamedCallback.class.getDeclaredMethod("renamedCallback", Object.class);
        check(WalletPreviewCandidates.select(List.of(getter), List.of(callback)).equals(List.of(getter, callback)), "Renamed relationship lost");
        check(WalletPreviewCandidates.select(List.of(getter, getter), List.of(callback, callback)).equals(List.of(getter, callback)), "Duplicate candidates rejected");
        check(WalletPreviewCandidates.select(List.of(getter), List.of(callback, AnotherCallback.class.getDeclaredMethod("another", Object.class))).isEmpty(), "Ambiguous callbacks selected");
        check(WalletPreviewCandidates.select(List.of(getter), List.of(OtherModelCallback.class.getDeclaredMethod("another", Object.class))).isEmpty(), "Unrelated model selected");
        check(!WalletPreviewCandidates.valid(List.of(callback, getter)), "Reversed descriptor roles accepted");
        check(!WalletPreviewCandidates.valid(List.of(getter)), "Incomplete binding accepted");
        check(!WalletPreviewCandidates.valid(List.of(getter, StaticCallback.class.getDeclaredMethod("another", Object.class))), "Static callback accepted");
        check(!WalletPreviewCandidates.valid(List.of(getter, WrongShape.class.getDeclaredMethod("another", int.class))), "Different coroutine signature accepted");
        check(WalletPreviewCandidates.select(List.of(OldModel.class.getDeclaredMethod("oldState")), List.of(callback)).isEmpty(), "Original UI accepted as new candidate");
        System.out.println("PASS: renamed candidates, uniqueness, graph relationship and unsupported shapes");
    }
    static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    public static class NewState { public CardInfo card, standaloneCard; public List<?> cards, valuables; }
    public static class OldState { public CardInfo card; public List<?> valuables; }
    public static class RenamedModel {
        final Application context;
        public RenamedModel(Application app) { context = app; }
        public NewState renamedState() { return null; }
    }
    public static class OldModel { public OldModel(Application app) {} public OldState oldState() { return null; } }
    public static class RenamedCallback { RenamedModel captured; public Object renamedCallback(Object value) { return value; } }
    public static class AnotherCallback { RenamedModel captured; public Object another(Object value) { return value; } }
    public static class OtherModelCallback { OldModel captured; public Object another(Object value) { return value; } }
    public static class StaticCallback { RenamedModel captured; public static Object another(Object value) { return value; } }
    public static class WrongShape { RenamedModel captured; public Object another(int value) { return value; } }
}
