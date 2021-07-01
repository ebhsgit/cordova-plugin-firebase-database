package by.chemerisuk.cordova.firebase;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import by.chemerisuk.cordova.support.CordovaMethod;
import by.chemerisuk.cordova.support.ReflectiveCordovaPlugin;

public class FirebaseDatabasePlugin extends ReflectiveCordovaPlugin {
    private final static String TAG = "FirebaseDatabasePlugin";

    private final static String EVENT_TYPE_VALUE = "value";
    private final static String EVENT_TYPE_CHILD_ADDED = "child_added";
    private final static String EVENT_TYPE_CHILD_CHANGED = "child_changed";
    private final static String EVENT_TYPE_CHILD_REMOVED = "child_removed";
    private final static String EVENT_TYPE_CHILD_MOVED = "child_moved";
    private final static Type settableType = new TypeToken<Map<String, Object>>() {
    }.getType();

    private Gson gson;
    private Map<String, Object> listeners;
    private boolean isDestroyed = false;

    @Override
    protected void pluginInitialize() {
        this.gson = new Gson();
        this.listeners = new HashMap<>();
    }

    @Override
    public void onDestroy() {
        this.isDestroyed = true;
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void get(String url, String path, JSONObject orderBy, JSONArray includes, JSONObject limit, long timeout, CallbackContext callbackContext) throws JSONException {
        final Query query = new QueryBuilder(getDb(url)).createQuery(path, orderBy, includes, limit);
        Task<DataSnapshot> task = query.get();
        try {
            DataSnapshot snapshot = Tasks.await(task, timeout, TimeUnit.SECONDS);
            // If `get()` could not reach server, it returns from cache.
            // How to determine if the snapshot was from cache?
            callbackContext.sendPluginResult(createPluginResult(snapshot, false));
        }
        catch (ExecutionException | InterruptedException e) {
            callbackContext.error(e.getMessage());
        }
        catch (TimeoutException e) {
            callbackContext.error("Timed out");
        }
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void on(String url, String path, String type, JSONObject orderBy, JSONArray includes, JSONObject limit, String uid, CallbackContext callbackContext) throws JSONException {
        final Query query = new QueryBuilder(getDb(url)).createQuery(path, orderBy, includes, limit);
        final boolean keepCallback = !uid.isEmpty();

        if (EVENT_TYPE_VALUE.equals(type)) {
            ValueEventListener valueListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else {
                        callbackContext.error(databaseError.getCode());
                    }
                }
            };

            if (keepCallback) {
                query.addValueEventListener(valueListener);
                listeners.put(uid, valueListener);
            }
            else {
                query.addListenerForSingleValueEvent(valueListener);
            }
        }
        else if (keepCallback) {
            final ChildEventListener childListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else if (EVENT_TYPE_CHILD_ADDED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else if (EVENT_TYPE_CHILD_CHANGED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else if (EVENT_TYPE_CHILD_REMOVED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else if (EVENT_TYPE_CHILD_MOVED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    }
                    else {
                        callbackContext.error(databaseError.getCode());
                    }
                }
            };

            query.addChildEventListener(childListener);
            listeners.put(uid, childListener);
        }
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void off(String url, String path, String uid, CallbackContext callbackContext) {
        Query query = getDb(url).getReference(path);
        Object listener = listeners.get(uid);
        if (listener instanceof ValueEventListener) {
            query.removeEventListener((ValueEventListener) listener);
        }
        else if (listener instanceof ChildEventListener) {
            query.removeEventListener((ChildEventListener) listener);
        }

        callbackContext.success();
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void set(String url, String path, Object value, Object priority, CallbackContext callbackContext) {
        DatabaseReference ref = getDb(url).getReference(path);
        DatabaseReference.CompletionListener listener = (error, ref1) -> {
            if (error != null) {
                callbackContext.error(error.getCode());
            }
            else {
                callbackContext.success();
            }
        };

        if (value == null && priority == null) {
            ref.removeValue(listener);
        }
        else if (priority == null) {
            ref.setValue(toSettable(value), listener);
        }
        else {
            ref.setValue(toSettable(value), priority, listener);
        }
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void update(String url, String path, JSONObject value, CallbackContext callbackContext) throws JSONException {
        Map<String, Object> updates = new HashMap<>();
        for (Iterator<String> it = value.keys(); it.hasNext(); ) {
            String key = it.next();
            updates.put(key, toSettable(value.get(key)));
        }

        DatabaseReference ref = getDb(url).getReference(path);
        ref.updateChildren(updates, (error, ref1) -> {
            if (error != null) {
                callbackContext.error(error.getCode());
            }
            else {
                callbackContext.success();
            }
        });
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void push(String url, String path, JSONObject value, CallbackContext callbackContext) {
        DatabaseReference ref = getDb(url).getReference(path).push();

        if (value == null) {
            callbackContext.success(ref.getKey());
        }
        else {
            ref.setValue(toSettable(value), (error, ref1) -> {
                if (error != null) {
                    callbackContext.error(error.getCode());
                }
                else {
                    callbackContext.success(ref1.getKey());
                }
            });
        }
    }

    @CordovaMethod(ExecutionThread.WORKER)
    private void setOnline(String url, boolean enabled, CallbackContext callbackContext) {
        if (enabled) {
            getDb(url).goOnline();
        }
        else {
            getDb(url).goOffline();
        }

        callbackContext.success();
    }

    private static FirebaseDatabase getDb(String url) {
        if (url.isEmpty()) {
            return FirebaseDatabase.getInstance();
        }
        else {
            return FirebaseDatabase.getInstance(url);
        }
    }

    private PluginResult createPluginResult(DataSnapshot dataSnapshot, boolean keepCallback) {
        JSONObject data = new JSONObject();
        Object value = dataSnapshot.getValue(false);
        try {
            data.put("priority", dataSnapshot.getPriority());
            data.put("key", dataSnapshot.getKey());
            if (value instanceof Map) {
                value = new JSONObject(this.gson.toJson(value));
            }
            else if (value instanceof List) {
                value = new JSONArray(this.gson.toJson(value));
            }
            data.put("value", value);
        }
        catch (JSONException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
        pluginResult.setKeepCallback(keepCallback);
        return pluginResult;
    }

    private Object toSettable(Object value) {
        Object result = value;

        if (value instanceof JSONObject) {
            result = this.gson.fromJson(value.toString(), settableType);
        }

        return result;
    }

    private static class QueryBuilder {
        private final FirebaseDatabase db;

        private QueryBuilder(FirebaseDatabase db) {
            this.db = db;
        }

        public Query createQuery(String path, JSONObject orderBy, JSONArray includes, JSONObject limit) throws JSONException {
            Query query = db.getReference(path);
            if (orderBy == null) {
                return query;
            }

            query = this.processOrderBy(query, orderBy);
            query = this.processIncludes(query, includes);
            query = this.processLimit(query, limit);

            return query;
        }

        private boolean isOrderByKey = false;

        private Query processOrderBy(Query query, JSONObject orderBy) throws JSONException {
            if (orderBy.has("key")) {
                this.isOrderByKey = true;
                return query.orderByKey();
            }

            if (orderBy.has("value"))
                return query.orderByValue();

            if (orderBy.has("priority"))
                return query.orderByPriority();

            if (orderBy.has("child"))
                return query.orderByChild(orderBy.getString("child"));

            throw new JSONException("'orderBy' is invalid");
        }

        private Query processIncludes(Query query, JSONArray includes) throws JSONException {
            if (includes == null) return query;

            Log.d(TAG, "QueryBuilder: processing 'includes'");

            for (int i = 0, n = includes.length(); i < n; ++i) {
                JSONObject filter = includes.getJSONObject(i);
                query = this.setFilter(query, filter);
            }

            return query;
        }

        private Query processLimit(Query query, JSONObject limit) throws JSONException {
            if (limit == null) return query;

            if (limit.has("first")) {
                Log.d(TAG, "QueryBuilder: 'limitToFirst'");
                return query.limitToFirst(limit.getInt("first"));
            }

            if (limit.has("last")) {
                Log.d(TAG, "QueryBuilder: 'limitToLast'");
                return query.limitToLast(limit.getInt("last"));
            }

            return query;
        }

        private Query setFilter(Query query, JSONObject filter) throws JSONException {
            //noinspection ConstantConditions
            @Nullable String key = filter.optString("key", null);

            Object startAt = filter.opt("startAt");
            if (startAt != null) return startAtHandler(query, startAt, key);

            Object startAfter = filter.opt("startAfter");
            if (startAfter != null) return startAfterHandler(query, startAfter, key);

            Object endBefore = filter.opt("endBefore");
            if (endBefore != null) return endBeforeHandler(query, endBefore, key);

            Object endAt = filter.opt("endAt");
            if (endAt != null) return endAtHandler(query, endAt, key);

            Object equalTo = filter.opt("equalTo");
            if (equalTo != null) return equalToHandler(query, equalTo, key);

            throw new JSONException("'includes' are invalid");
        }

        private Query startAtHandler(Query query, Object value, String key) {
            Log.d(TAG, "QueryBuilder: processing startAt");

            if (isOrderByKey) {
                if (value instanceof Number) return query.startAt((Double) value);
                if (value instanceof Boolean) return query.startAt((Boolean) value);
                return query.startAt(value.toString());
            }
            else {
                if (value instanceof Number) return query.startAt((Double) value, key);
                if (value instanceof Boolean) return query.startAt((Boolean) value, key);
                return query.startAt(value.toString(), key);
            }
        }

        private Query startAfterHandler(Query query, Object value, String key) {
            Log.d(TAG, "QueryBuilder: processing startAfter");

            if (isOrderByKey) {
                if (value instanceof Number) return query.startAfter((Double) value);
                if (value instanceof Boolean) return query.startAfter((Boolean) value);
                return query.startAfter(value.toString());
            }
            else {
                if (value instanceof Number) return query.startAfter((Double) value, key);
                if (value instanceof Boolean) return query.startAfter((Boolean) value, key);
                return query.startAfter(value.toString(), key);
            }
        }

        private Query endBeforeHandler(Query query, Object value, String key) {
            Log.d(TAG, "QueryBuilder: processing endBefore");

            if (isOrderByKey) {
                if (value instanceof Number) return query.endBefore((Double) value);
                if (value instanceof Boolean) return query.endBefore((Boolean) value);
                return query.endBefore(value.toString());
            }
            else {
                if (value instanceof Number) return query.endBefore((Double) value, key);
                if (value instanceof Boolean) return query.endBefore((Boolean) value, key);
                return query.endBefore(value.toString(), key);
            }
        }

        private Query endAtHandler(Query query, Object value, String key) {
            Log.d(TAG, "QueryBuilder: processing endAt");

            if (isOrderByKey) {
                if (value instanceof Number) return query.endAt((Double) value);
                if (value instanceof Boolean) return query.endAt((Boolean) value);
                return query.endAt(value.toString());
            }
            else {
                if (value instanceof Number) return query.endAt((Double) value, key);
                if (value instanceof Boolean) return query.endAt((Boolean) value, key);
                return query.endAt(value.toString(), key);
            }
        }

        private Query equalToHandler(Query query, Object value, String key) {
            Log.d(TAG, "QueryBuilder: processing equalTo");

            if (isOrderByKey) {
                if (value instanceof Number) return query.equalTo((Double) value);
                if (value instanceof Boolean) return query.equalTo((Boolean) value);
                return query.equalTo(value.toString());
            }
            else {
                if (value instanceof Number) return query.equalTo((Double) value, key);
                if (value instanceof Boolean) return query.equalTo((Boolean) value, key);
                return query.equalTo(value.toString(), key);
            }
        }
    }
}