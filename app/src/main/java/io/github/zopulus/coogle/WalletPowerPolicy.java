package io.github.zopulus.coogle;

/** Selects Google payment for the native system wallet route. */
public final class WalletPowerPolicy {
    private WalletPowerPolicy() { }

    public static boolean isGooglePayment(String paymentComponent) {
        return paymentComponent != null
                && paymentComponent.startsWith("com.google.android.gms/");
    }
}
