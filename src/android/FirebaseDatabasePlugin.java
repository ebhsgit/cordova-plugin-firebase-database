package by.chemerisuk.cordova.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseDatabasePlugin extends CordovaPlugin {
    private FirebaseDatabase database;
    private Map<String, Object> listeners;

    @Override
    protected void pluginInitialize() {
        this.database = FirebaseDatabase.getInstance();
        this.listeners = new HashMap<String, Object>();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("on".equals(action)) {
            on(args, callbackContext);
        } else if ("off".equals(action)) {
            off(args, callbackContext);
        // } else if ("update".equals(action)) {
        //     update(args, callbackContext);
        } else if ("set".equals(action)) {
            set(args, callbackContext);
        } else if ("setOnline".equals(action)) {
            setOnline(args.getBoolean(0), callbackContext);
        // } else if ("push".equals(action)) {
        //     push(args, callbackContext);
        } else {
            return false;
        }

        return true;
    }

    private void on(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String type = args.getString(0);
        final String path = args.getString(1);
        final Query query = createQuery(path, args.optJSONObject(2), args.optJSONArray(3), args.optJSONObject(4));

        final String uid = args.getString(5);
        final boolean keepCallback = uid != null;

        if ("value".equals(type)) {
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    callbackContext.error(databaseError.getCode());
                }
            };

            if (keepCallback) {
                listeners.put(uid, query.addValueEventListener(listener));
            } else {
                query.addListenerForSingleValueEvent(listener);
            }
        } else if (keepCallback) {
            listeners.put(uid, query.addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    if ("child_added".equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    if ("child_changed".equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                    if ("child_removed".equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    if ("child_moved".equals(type)) {
                        callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    callbackContext.error(databaseError.getCode());
                }
            }));
        }
    }

    private void off(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final String uid = args.getString(0);
        final String type = args.getString(1);
        final String path = args.getString(2);

        Query query = this.database.getReference(path);
        Object listener = listeners.get(uid);
        if (listener instanceof ValueEventListener) {
            query.removeEventListener((ValueEventListener)listener);
        } else if (listener instanceof ChildEventListener) {
            query.removeEventListener((ChildEventListener)listener);
        } else {
            throw new JSONException("listener is invalid");
        }

        callbackContext.success();
    }

    private void set(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Object value = args.get(1);
        Object priority = args.get(2);

        DatabaseReference ref = this.database.getReference(args.getString(0));
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
            ref.setValue(value, listener);
        } else {
            ref.setValue(value, priority, listener);
        }
    }

    private void setOnline(boolean enabled, CallbackContext callbackContext) {
        if (enabled) {
            database.goOnline();
        } else {
            database.goOffline();
        }

        callbackContext.success();
    }

    private Query createQuery(String path, JSONObject orderBy, JSONArray includes, JSONObject limit) throws JSONException {
        Query query = this.database.getReference(path);

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

    private static PluginResult createPluginResult(DataSnapshot dataSnapshot, boolean keepCallback) {
        JSONObject data = new JSONObject();
        Object value = dataSnapshot.getValue(false);
        try {
            data.put("priority", dataSnapshot.getPriority());
            data.put("key", dataSnapshot.getKey());
            if (value instanceof Map) {
                value = toJSON((Map<String, Object>)value);
            } else if (value instanceof List) {
                value = new JSONArray((List)value);
            }
            data.put("value", value);
        } catch (JSONException e) {}

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
        pluginResult.setKeepCallback(keepCallback);
        return pluginResult;
    }

    private static JSONObject toJSON(Map<String, Object> values) throws JSONException {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = new JSONObject((Map)value);
            } else if (value instanceof List) {
                value = new JSONArray((List)value);
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }
}
