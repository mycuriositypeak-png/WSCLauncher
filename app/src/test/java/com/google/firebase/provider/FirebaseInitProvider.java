package com.google.firebase.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.content.Context;

/** Test-only stand-in; no Firebase SDK is packaged in the launcher. */
public final class FirebaseInitProvider extends ContentProvider {
    public static Context initializedContext;
    public static int calls;
    public static RuntimeException failure;
    @Override public boolean onCreate() {
        calls++;
        initializedContext = getContext();
        if (failure != null) throw failure;
        return false; // The real FirebaseInitProvider returns false on success too.
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
