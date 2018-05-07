package by.chemerisuk.cordova.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseDatabasePlugin extends CordovaPlugin {
    private final static String EVENT_TYPE_VALUE = "value";
    private final static String EVENT_TYPE_CHILD_ADDED = "child_added";
    private final static String EVENT_TYPE_CHILD_CHANGED = "child_changed";
    private final static String EVENT_TYPE_CHILD_REMOVED = "child_removed";
    private final static String EVENT_TYPE_CHILD_MOVED = "child_moved";
    private final static Type settableType = new TypeToken<Map<String, Object>>() {}.getType();

    private Gson gson;
    private Map<String, Object> listeners;
    private boolean isDestroyed = false;

    @Override
    protected void pluginInitialize() {
        this.gson = new Gson();
        this.listeners = new HashMap<String, Object>();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("on".equals(action)) {
            on(args, callbackContext);
        } else if ("off".equals(action)) {
            off(args, callbackContext);
        } else if ("set".equals(action)) {
            set(args, callbackContext);
        } else if ("update".equals(action)) {
            update(args, callbackContext);
        } else if ("push".equals(action)) {
            push(args, callbackContext);
        } else if ("setOnline".equals(action)) {
            setOnline(args.optString(0), args.getBoolean(1), callbackContext);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public void onDestroy() {
        this.isDestroyed = true;
    }

    private void on(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String url = args.optString(0);
        final String path = args.getString(1);
        final String type = args.getString(2);
        final String uid = args.getString(6);
        final boolean keepCallback = !uid.isEmpty();

        final Query query = createQuery(url, path, args.optJSONObject(3), args.optJSONArray(4), args.optJSONObject(5));

        if (EVENT_TYPE_VALUE.equals(type)) {
            ValueEventListener valueListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else {
                        callbackContext.error(databaseError.getCode());
                    }
                }
            };

            if (keepCallback) {
                query.addValueEventListener(valueListener);
                listeners.put(uid, valueListener);
            } else {
                query.addListenerForSingleValueEvent(valueListener);
            }
        } else if (keepCallback) {
            final ChildEventListener childListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else if (EVENT_TYPE_CHILD_ADDED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else if (EVENT_TYPE_CHILD_CHANGED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else if (EVENT_TYPE_CHILD_REMOVED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else if (EVENT_TYPE_CHILD_MOVED.equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    if (isDestroyed) {
                        query.removeEventListener(this);
                    } else {
                        callbackContext.error(databaseError.getCode());
                    }
                }
            };

            query.addChildEventListener(childListener);
            listeners.put(uid, childListener);
        }
    }

    private void off(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String url = args.optString(0);
        final String path = args.getString(1);
        final String uid = args.getString(2);

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Query query = getDb(url).getReference(path);
                Object listener = listeners.get(uid);
                if (listener instanceof ValueEventListener) {
                    query.removeEventListener((ValueEventListener)listener);
                } else if (listener instanceof ChildEventListener) {
                    query.removeEventListener((ChildEventListener)listener);
                }

                callbackContext.success();
            }
        });
    }

    private void set(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String url = args.optString(0);
        final String path = args.getString(1);
        final Object value = args.get(2);
        final Object priority = args.opt(3);

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                DatabaseReference ref = getDb(url).getReference(path);
                DatabaseReference.CompletionListener listener = new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference ref) {
                        if (error != null) {
                            callbackContext.error(error.getCode());
                        } else {
                            callbackContext.success();
                        }
                    }
                };

                if (value == null && priority == null) {
                    ref.removeValue(listener);
                } else if (priority == null) {
                    ref.setValue(toSettable(value), listener);
                } else {
                    ref.setValue(toSettable(value), priority, listener);
                }
            }
        });
    }

    private void update(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String url = args.optString(0);
        final String path = args.getString(1);
        final JSONObject value = args.optJSONObject(2);
        final Map<String, Object> updates = new HashMap<String, Object>();
        for (Iterator<String> it = value.keys(); it.hasNext(); ) {
            String key = it.next();
            updates.put(key, toSettable(value.get(key)));
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                DatabaseReference ref = getDb(url).getReference(path);

                ref.updateChildren(updates, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError error, DatabaseReference ref) {
                        if (error != null) {
                            callbackContext.error(error.getCode());
                        } else {
                            callbackContext.success();
                        }
                    }
                });
            }
        });
    }

    private void push(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String url = args.optString(0);
        final String path = args.getString(1);
        final Object value = args.get(2);

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                final DatabaseReference ref = getDb(url).getReference(path).push();

                if (value == null) {
                    callbackContext.success(ref.getKey());
                } else {
                    ref.setValue(toSettable(value), new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(DatabaseError error, DatabaseReference ref) {
                            if (error != null) {
                                callbackContext.error(error.getCode());
                            } else {
                                callbackContext.success(ref.getKey());
                            }
                        }
                    });
                }
            }
        });
    }

    private void setOnline(final String url, final boolean enabled, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                if (enabled) {
                    getDb(url).goOnline();
                } else {
                    getDb(url).goOffline();
                }

                callbackContext.success();
            }
        });
    }

    private Query createQuery(String url, String path, JSONObject orderBy, JSONArray includes, JSONObject limit) throws JSONException {
        Query query = getDb(url).getReference(path);

        if (orderBy != null) {
            if (orderBy.has("key")) {
                query = query.orderByKey();
            } else if (orderBy.has("value")) {
                query = query.orderByValue();
            } else if (orderBy.has("priority")) {
                query = query.orderByPriority();
            } else if (orderBy.has("child")) {
                query = query.orderByChild(orderBy.getString("child"));
            } else {
                throw new JSONException("order is invalid");
            }

            for (int i = 0, n = includes.length(); i < n; ++i) {
                JSONObject filters = includes.getJSONObject(i);

                String key = filters.optString("key");
                Object endAt = filters.opt("endAt");
                Object startAt = filters.opt("startAt");
                Object equalTo = filters.opt("equalTo");

                if (startAt != null) {
                    if (startAt instanceof Number) {
                        query = query.startAt((Double)startAt, key);
                    } else if (startAt instanceof Boolean) {
                        query = query.startAt((Boolean)startAt, key);
                    } else {
                        query = query.startAt(startAt.toString(), key);
                    }
                } else if (endAt != null) {
                    if (endAt instanceof Number) {
                        query = query.endAt((Double)endAt, key);
                    } else if (endAt instanceof Boolean) {
                        query = query.endAt((Boolean)endAt, key);
                    } else {
                        query = query.endAt(endAt.toString(), key);
                    }
                } else if (equalTo != null) {
                    if (equalTo instanceof Number) {
                        query = query.equalTo((Double)equalTo, key);
                    } else if (equalTo instanceof Boolean) {
                        query = query.equalTo((Boolean)equalTo, key);
                    } else {
                        query = query.equalTo(equalTo.toString(), key);
                    }
                } else {
                    throw new JSONException("includes are invalid");
                }
            }

            if (limit != null) {
                if (limit.has("first")) {
                    query = query.limitToFirst(limit.getInt("first"));
                } else if (limit.has("last")) {
                    query = query.limitToLast(limit.getInt("last"));
                }
            }
        }

        return query;
    }

    private static FirebaseDatabase getDb(String url) {
        if (url.isEmpty()) {
            return FirebaseDatabase.getInstance();
        } else {
            return FirebaseDatabase.getInstance(url);
        }
    }

    private static PluginResult createPluginResult(DataSnapshot dataSnapshot, boolean keepCallback) {
        JSONObject data = new JSONObject();
        Object value = dataSnapshot.getValue(false);
        try {
            data.put("priority", dataSnapshot.getPriority());
            data.put("key", dataSnapshot.getKey());
            if (value instanceof Map) {
                value = new JSONObject(this.gson.toJson(value));
            } else if (value instanceof List) {
                value = new JSONArray(this.gson.toJson(value));
            }
            data.put("value", value);
        } catch (JSONException e) {}

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
}
