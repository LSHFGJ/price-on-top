package dev.priceontop.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceState;

public final class PriceTopProvider extends ContentProvider {
    private PriceStorage storage;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            storage = new AndroidPrivatePriceStorage(context.getApplicationContext());
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        AccessRequest request = currentAccessRequest();
        enforceAllowed(request);

        if (PriceTopContract.METHOD_GET_CONFIG.equals(method)) {
            return configBundle(false);
        }
        if (PriceTopContract.METHOD_GET_REFRESH_CONFIG.equals(method)) {
            return configBundle(true);
        }
        if (PriceTopContract.METHOD_GET_CACHE.equals(method)) {
            return cacheBundle();
        }
        if (PriceTopContract.METHOD_SAVE_CACHE.equals(method)) {
            PriceState state = PriceTopContract.stateFromBundle(extras);
            if (storage != null) {
                storage.saveState(state);
            }
            return statusBundle("ok");
        }
        if (PriceTopContract.METHOD_SAVE_CONFIG.equals(method)) {
            if (request.callerUid != request.ownUid) {
                throw new SecurityException(ProviderAccess.deniedDiagnostic(request.callerUid, request.packageNames, "save_config requires own uid"));
            }
            return statusBundle("save_config_reserved_for_app_uid");
        }
        throw new IllegalArgumentException("Unsupported PriceTopProvider method");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        enforceAllowed(currentAccessRequest());
        throw new UnsupportedOperationException("PriceTopProvider exposes call() only");
    }

    @Override
    public String getType(Uri uri) {
        enforceAllowed(currentAccessRequest());
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        enforceAllowed(currentAccessRequest());
        throw new UnsupportedOperationException("PriceTopProvider exposes call() only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        enforceAllowed(currentAccessRequest());
        throw new UnsupportedOperationException("PriceTopProvider exposes call() only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        enforceAllowed(currentAccessRequest());
        throw new UnsupportedOperationException("PriceTopProvider exposes call() only");
    }

    private Bundle configBundle(boolean includeSensitive) {
        PriceConfig config = storage == null ? null : storage.loadConfig();
        Bundle bundle = statusBundle("ok");
        if (config != null) {
            PriceTopContract.putConfig(bundle, config, includeSensitive);
            PriceTopContract.putSystemUiDefaults(bundle, false);
            return bundle;
        }
        PriceTopContract.putSystemUiDefaults(bundle, false);
        return bundle;
    }

    private Bundle cacheBundle() {
        PriceState state = storage == null ? PriceState.empty() : storage.loadState();
        Bundle bundle = statusBundle("ok");
        PriceTopContract.putState(bundle, state);
        return bundle;
    }

    private static Bundle statusBundle(String status) {
        Bundle bundle = new Bundle();
        bundle.putString(PriceTopContract.KEY_STATUS, status);
        bundle.putBoolean(PriceTopContract.KEY_ALLOWED, true);
        return bundle;
    }

    private AccessRequest currentAccessRequest() {
        int callerUid = Binder.getCallingUid();
        int ownUid = Process.myUid();
        String[] packageNames = null;
        Context context = getContext();
        if (context != null) {
            PackageManager packageManager = context.getPackageManager();
            if (packageManager != null) {
                packageNames = packageManager.getPackagesForUid(callerUid);
            }
        }
        return new AccessRequest(callerUid, ownUid, packageNames);
    }

    private static void enforceAllowed(AccessRequest request) {
        if (!ProviderAccess.isAllowed(request.callerUid, request.ownUid, request.packageNames)) {
            throw new SecurityException(ProviderAccess.deniedDiagnostic(request.callerUid, request.packageNames, "provider call"));
        }
    }

    private static final class AccessRequest {
        private final int callerUid;
        private final int ownUid;
        private final String[] packageNames;

        private AccessRequest(int callerUid, int ownUid, String[] packageNames) {
            this.callerUid = callerUid;
            this.ownUid = ownUid;
            this.packageNames = packageNames;
        }
    }
}
