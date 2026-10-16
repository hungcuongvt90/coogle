package io.github.zopulus.coogle;

import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import java.nio.charset.StandardCharsets;

/** Lens direct-intent caller callback; reports the actual shortcut application. */
final class ActionButtonLensCaller extends Binder {
    private final String caller;

    ActionButtonLensCaller(String caller) { this.caller = caller; }

    @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        // The native shortcut does not consume the SDK's activity result.
        if (code == 2) return true;
        if (code != 1 || reply == null) return super.onTransact(code, request, reply, flags);
        Bundle input = request.readBundle(Bundle.class.getClassLoader());
        byte[] challenge = input == null ? null : input.getByteArray("challenge");
        if (challenge == null) return false;
        byte[] response = challenge;
        if (challenge.length != 0) {
            int hash = 0x84222325;
            for (byte value : challenge) hash = (hash ^ value) * 435;
            response = Long.toHexString(Integer.toUnsignedLong(hash)).getBytes(StandardCharsets.UTF_8);
        }
        Bundle result = new Bundle();
        result.putInt("version", 1);
        result.putByteArray("challenge_reply", response);
        result.putString("caller_package", caller);
        reply.writeNoException();
        reply.writeBundle(result);
        return true;
    }
}
