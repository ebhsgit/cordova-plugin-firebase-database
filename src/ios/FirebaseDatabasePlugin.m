#import "FirebaseDatabasePlugin.h"

@implementation FirebaseDatabasePlugin

- (void)pluginInitialize {
    NSLog(@"Starting Firebase Database plugin");

    self.database = [FIRDatabase database];
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
    FIRDatabaseReference *ref = [self.database referenceWithPath:path];
    id value = [command argumentAtIndex:1];

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
}

- (void)once:(CDVInvokedUrlCommand *)command {
}

- (void)off:(CDVInvokedUrlCommand *)command {
}

@end
