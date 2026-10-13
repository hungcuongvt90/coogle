package io.github.zopulus.coogle;

public final class WalletPowerPolicyTest {
    public static void main(String[] args) {
        check(WalletPowerPolicy.isGooglePayment("com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"), "Google payment");
        check(!WalletPowerPolicy.isGooglePayment(null), "unset payment");
        check(!WalletPowerPolicy.isGooglePayment("other/com.google.android.gms"), "other payment package");
        check(!WalletPowerPolicy.isGooglePayment("com.google.android.gms.fake/a"), "similar package name");
        System.out.println("WalletPowerPolicyTest passed");
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
