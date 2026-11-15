package rikka.rish;

import android.os.Parcel;

/** Optional RISH transport is intentionally disabled in the embedded server. */
public abstract class RishService {
    public abstract void enforceCallingPermission(String func);
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        return false;
    }
}
