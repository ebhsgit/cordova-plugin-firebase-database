#import "FirebaseDatabasePlugin.h"

@implementation FirebaseDatabasePlugin

- (void)pluginInitialize {
    NSLog(@"Starting Firebase Database plugin");

    self.database = [FIRDatabase database];
    self.listeners = [NSMutableDictionary dictionary];
}

- (void)setOnline:(CDVInvokedUrlCommand *)command {
    BOOL enabled = [[command argumentAtIndex:0] boolValue];

    if (enabled) {
        [self.database goOnline];
    } else {
        [self.database goOffline];
    }
}

- (void)set:(CDVInvokedUrlCommand *)command {
    NSString *path = [command argumentAtIndex:0 withDefault:@"/" andClass:[NSString class]];
    id value = [command argumentAtIndex:1];
    FIRDatabaseReference *ref = [self.database referenceWithPath:path];

    [ref setValue:value withCompletionBlock:^(NSError *error, FIRDatabaseReference *ref) {
        CDVPluginResult *pluginResult;
        if (error) {

            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:@{
                    @"code": @(error.code),
                    @"message": error.description
            }];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:path];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)on:(CDVInvokedUrlCommand *)command {
    NSString *path = [command argumentAtIndex:0 withDefault:@"/" andClass:[NSString class]];
    FIRDataEventType type = [self stringToType:[command.arguments objectAtIndex:1]];
    FIRDatabaseReference *ref = [self.database referenceWithPath:path];

    NSDictionary* orderBy = [command.arguments objectAtIndex:2];
    NSArray* includes = [command.arguments objectAtIndex:3];
    NSDictionary* limit = [command.arguments objectAtIndex:4];
    FIRDatabaseQuery *query = [self createQuery:ref withOrderBy:orderBy];
    for (NSDictionary* condition in includes) {
        query = [self filterQuery:query withCondition:condition];
    }
    query = [self limitQuery:query withCondition:limit];

    NSString *uid = [command.arguments objectAtIndex:5];
    id handler = ^(FIRDataSnapshot *_Nonnull snapshot) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{
            @"key": snapshot.key,
            @"value": snapshot.value,
            @"priority": snapshot.priority
        }];
        [pluginResult setKeepCallbackAsBool:(uid ? YES : NO)];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    };

    if (uid) {
        FIRDatabaseHandle handle = [query observeEventType:type withBlock:handler];
        [self.listeners setObject:@(handle) forKey:uid];
    } else {
        [query observeSingleEventOfType:type withBlock:handler];
    }
}

- (void)off:(CDVInvokedUrlCommand *)command {
    NSString *path = [command argumentAtIndex:0 withDefault:@"/" andClass:[NSString class]];
    NSString *uid = [command.arguments objectAtIndex:1];
    FIRDatabaseReference *ref = [self.database referenceWithPath:path];

    [ref removeObserverWithHandle:self.listeners[uid]];
    [self.listeners removeObjectForKey:uid];

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (FIRDatabaseQuery *)createQuery:(FIRDatabaseReference *)ref withOrderBy:(NSDictionary *)orderBy {
    if ([orderBy class] != [NSNull class]) {
        if ([orderBy objectForKey:@"key"]) {
            return [ref queryOrderedByKey];
        } else if ([orderBy objectForKey:@"value"]) {
            return [ref queryOrderedByValue];
        } else if ([orderBy objectForKey:@"priority"]) {
            return [ref queryOrderedByPriority];
        } else {
            NSString* path = [orderBy objectForKey:@"child"];
            if (path) {
                return [ref queryOrderedByChild:path];
            }
        }
    }

    return ref;
}

- (FIRDatabaseQuery *)filterQuery:(FIRDatabaseQuery *)query withCondition:(NSDictionary *)condition {
    if ([condition class] != [NSNull class]) {
        NSString* childKey = [condition objectForKey:@"key"];
        id endAt = [condition objectForKey:@"endAt"];
        id startAt = [condition objectForKey:@"startAt"];
        id equalTo = [condition objectForKey:@"equalTo"];

        if (startAt) {
            return [query queryStartingAtValue:startAt childKey:childKey];
        } else if (endAt) {
            return [query queryEndingAtValue:endAt childKey:childKey];
        } else if (equalTo) {
            return [query queryEqualToValue:equalTo childKey:childKey];
        } // else throw error?
    }

    return query;
}

- (FIRDatabaseQuery *)limitQuery:(FIRDatabaseQuery *)query withCondition:(NSDictionary *)condition {
    if ([condition class] != [NSNull class]) {
        id first = [condition objectForKey:@"first"];
        id last = [condition objectForKey:@"last"];

        if (first) {
            return [query queryLimitedToFirst:[first integerValue]];
        } else if (last) {
            return [query queryLimitedToLast:[last integerValue]];
        }
    }

    return query;
}

- (FIRDataEventType)stringToType:(NSString *)type {
    if ([type isEqualToString:@"value"]) {
        return FIRDataEventTypeValue;
    } else if ([type isEqualToString:@"child_added"]) {
        return FIRDataEventTypeChildAdded;
    } else if ([type isEqualToString:@"child_removed"]) {
        return FIRDataEventTypeChildRemoved;
    } else if ([type isEqualToString:@"child_changed"]) {
        return FIRDataEventTypeChildChanged;
    } else if ([type isEqualToString:@"child_moved"]) {
        return FIRDataEventTypeChildMoved;
    }
}

@end
