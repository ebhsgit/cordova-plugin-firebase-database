var exec = require("cordova/exec");
var PLUGIN_NAME = "FirebaseDatabase";

function DbQuery(ref, orderBy) {
    this.ref = ref;
    this._orderBy = orderBy;
}

DbQuery.prototype = {
    endAt: function(value, key) {
        this._filter = {endAt: value, key: key};
        return this;
    },
    startAt: function(value, key) {
        this._filter = {startAt: value, key: key};
        return this;
    },
    equalTo: function(value, key) {
        this._filter = {equalTo: value, key: key};
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
    on: function(eventType, callback, errorCallback) {
        exec(callback, errorCallback, PLUGIN_NAME, "on",
            [eventType, this._path, this._orderBy, this._filter, this._limit]);
    },
    once: function(eventType, callback, errorCallback) {
        exec(callback, errorCallback, PLUGIN_NAME, "once",
            [eventType, this._path, this._orderBy, this._filter, this._limit]);
    },
    off: function(eventType, callback) {

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
    this._path = path;
}

DbRef.prototype = new DbQuery();

module.exports = {
    ref: function(path) {
        return new DbRef(path);
    },
    goOffline: function() {

    },
    goOnline: function() {

    }
};
