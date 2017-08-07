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
        final JSONObject filter = args.optJSONObject(3);
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

        // cordova.getThreadPool().execute(new Runnable() {
        //     @Override
        //     public void run() {
                if ("value".equals(type)) {
                    query.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            callbackContext.sendPluginResult(createPluginResult(snapshot, keepCallback));
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            callbackContext.error(databaseError.getCode());
                        }
                    });
                } else {
                    query.addChildEventListener(new ChildEventListener() {
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
                    });
                }
        //     }
        // });
    }

    private static PluginResult createPluginResult(DataSnapshot dataSnapshot, boolean keepCallback) {
        JSONObject data = new JSONObject();
        try {
            data.put("key", dataSnapshot.getKey());
            data.put("priority", dataSnapshot.getPriority());
            data.put("value", dataSnapshot.getValue());
        } catch (JSONException e) {}

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
        pluginResult.setKeepCallback(keepCallback);
        return pluginResult;
    }
}
