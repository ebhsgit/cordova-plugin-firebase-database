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

    @Override
    protected void pluginInitialize() {
        this.database = FirebaseDatabase.getInstance();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("once".equals(action)) {
            on(args, callbackContext, false);
        } else if ("on".equals(action)) {
            on(args, callbackContext, true);
        // } else if ("off".equals(action)) {
        //     off(args, callbackContext);
        // } else if ("update".equals(action)) {
        //     update(args, callbackContext);
        // } else if ("set".equals(action)) {
        //     set(args, callbackContext);
        // } else if ("remove".equals(action)) {
        //     remove(args, callbackContext);
        // } else if ("push".equals(action)) {
        //     push(args, callbackContext);
        } else {
            return false;
        }

        return true;
    }

    private void on(JSONArray args, final CallbackContext callbackContext, final boolean keepCallback) throws JSONException {
        final String type = args.getString(0);
        final String path = args.getString(1);
        final JSONObject orderBy = args.optJSONObject(2);
        final JSONArray includes = args.optJSONArray(3);
        final int limit = args.optInt(4);

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
        }

        // TODO: _filter, _limit

        final DatabaseReference ref = query.getRef();

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                if ("value".equals(type)) {
                    ref.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            callbackContext.sendPluginResult(createPluginResult(snapshot, null, keepCallback));
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            callbackContext.error(databaseError.getCode());
                        }
                    });
                } else {
                    ref.addChildEventListener(new ChildEventListener() {
                        @Override
                        public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                            if ("child_added".equals(type)) {
                                callbackContext.sendPluginResult(createPluginResult(snapshot, previousChildName, keepCallback));
                            }
                        }

                        @Override
                        public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                            if ("child_changed".equals(type)) {
                                callbackContext.sendPluginResult(createPluginResult(snapshot, previousChildName, keepCallback));
                            }
                        }

                        @Override
                        public void onChildRemoved(DataSnapshot snapshot) {
                            if ("child_removed".equals(type)) {
                                callbackContext.sendPluginResult(createPluginResult(snapshot, null, keepCallback));
                            }
                        }

                        @Override
                        public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                            if ("child_moved".equals(type)) {
                                callbackContext.sendPluginResult(createPluginResult(snapshot, previousChildName, keepCallback));
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            callbackContext.error(databaseError.getCode());
                        }
                    });
                }
            }
        });
    }

    private static PluginResult createPluginResult(DataSnapshot dataSnapshot, String previousChildName, boolean keepCallback) {
        JSONObject data = new JSONObject();
        Object value = dataSnapshot.getValue(false);
        try {
            data.put("previousChildName", previousChildName);
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
