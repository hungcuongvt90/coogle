package android.os;
public class Binder implements IBinder {
    protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException { return false; }
    public static int cleared, restored;
    public static long clearCallingIdentity() { cleared++; return 73; }
    public static void restoreCallingIdentity(long token) {
        if (token != 73) throw new AssertionError("Incorrect identity token");
        restored++;
    }
}
