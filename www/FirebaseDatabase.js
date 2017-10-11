var exec = require("cordova/exec");
var utils = require("cordova/utils");
var PLUGIN_NAME = "FirebaseDatabase";
var noop = function() {};

function DbSnapshot(ref, data) {
    this.ref = ref;
    this.key = data.key;
    this._data = data;
}

DbSnapshot.prototype = {
    val: function() {
        return this._data.value;
    },
    getPriority: function() {
        return this._data.priority;
    }
};

function DbQuery(ref, orderBy) {
    this.ref = ref;
    this._orderBy = orderBy;
    this._includes = [];
    this._limit = {};
}

DbQuery.prototype = {
    endAt: function(value, key) {
        this._includes.push({endAt: value, key: key});
        return this;
    },
    startAt: function(value, key) {
        this._includes.push({startAt: value, key: key});
        return this;
    },
    equalTo: function(value, key) {
        this._includes.push({equalTo: value, key: key});
        return this;
    },
    limitToFirst: function(limit) {
        this._limit = {first: limit};
        return this;
    },
    limitToLast: function(limit) {
        this._limit = {last: limit};
        return this;
    },
    on: function(eventType, success, error) {
        var ref = this.ref;
        var callback = function(data) {
                success(new DbSnapshot(ref, data));
            };

        callback._id = utils.createUUID();

        exec(callback, error, PLUGIN_NAME, "on",
            [ref._path, eventType, this._orderBy, this._includes, this._limit, callback._id]);

        return callback;
    },
    once: function(eventType) {
        var args = [ref._path, eventType, this._orderBy, this._includes, this._limit, ""];
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, "on", args);
        }).then(function(data) {
            return new DbSnapshot(this.ref, data);
        }.bind(this));
    },
    off: function(eventType, callback) {
        var args = [ref._path, callback._id];
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, "off", args);
        });
    },
    orderByChild: function(path) {
        return new DbQuery(this.ref, {child: path});
    },
    orderByKey: function() {
        return new DbQuery(this.ref, {key: true});
    },
    orderByPriority: function() {
        return new DbQuery(this.ref, {priority: true});
    },
    orderByValue: function() {
        return new DbQuery(this.ref, {value: true});
    }
};

function DbRef(path) {
    this.ref = this;
    this._path = path;
}

DbRef.prototype = new DbQuery();

DbRef.prototype.child = function(path) {
    return new DbRef(this._path.split("/").concat(path.split("/")).join("/"));
};

DbRef.prototype.remove = function() {
    var args = [this._path];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "set", args);
    });
};

DbRef.prototype.set = function(value) {
    var args = [this._path, value];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "set", args);
    });
};

DbRef.prototype.push = function(value) {
    var args = [this._path, value];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "push", args);
    }).then(function(path) {
        return new DbRef(path);
    });
};

DbRef.prototype.update = function(value) {
    var args = [this._path, value];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "update", args);
    });
};

DbRef.prototype.setPriority = function(priority) {
    var args = [this._path, null, priority];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "set", args);
    });
};

DbRef.prototype.setWithPriority = function(value, priority) {
    var args = [this._path, value, priority];
    return new Promise(function(resolve, reject) {
        exec(resolve, reject, PLUGIN_NAME, "set", args);
    });
};

module.exports = {
    ref: function(path) {
        return new DbRef(path);
    },
    goOnline: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, "setOnline", [true]);
        });
    },
    goOffline: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, "setOnline", [false]);
        });
    }
};
