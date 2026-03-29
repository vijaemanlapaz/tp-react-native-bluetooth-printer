//
//  RNBluetoothManager.m
//  RNBluetoothEscposPrinter
//
 

#import <Foundation/Foundation.h>
#import "RNBluetoothManager.h"
#import <CoreBluetooth/CoreBluetooth.h>
@implementation RNBluetoothManager

NSString *EVENT_DEVICE_ALREADY_PAIRED = @"EVENT_DEVICE_ALREADY_PAIRED";
NSString *EVENT_DEVICE_DISCOVER_DONE = @"EVENT_DEVICE_DISCOVER_DONE";
NSString *EVENT_DEVICE_FOUND = @"EVENT_DEVICE_FOUND";
NSString *EVENT_CONNECTION_LOST = @"EVENT_CONNECTION_LOST";
NSString *EVENT_UNABLE_CONNECT=@"EVENT_UNABLE_CONNECT";
NSString *EVENT_CONNECTED=@"EVENT_CONNECTED";
static NSArray<CBUUID *> *supportServices = nil;
static NSDictionary *writeableCharactiscs = nil;
bool hasListeners;

// Connection pool: maps peripheral UUID string → CBPeripheral
static NSMutableDictionary<NSString *, CBPeripheral *> *connectedDevices;

static RNBluetoothManager *instance;
static NSObject<WriteDataToBleDelegate> *writeDataDelegate;
static NSData *toWrite;
static NSTimer *timer;

// Track which address we are currently writing to
static NSString *currentWriteAddress;

+(void)initialize {
    if (self == [RNBluetoothManager class]) {
        connectedDevices = [[NSMutableDictionary alloc] init];
    }
}

+(Boolean)isConnected {
    return connectedDevices != nil && [connectedDevices count] > 0;
}

+(Boolean)isConnectedToAddress:(NSString *)address {
    if (address == nil) {
        return [self isConnected];
    }
    return connectedDevices != nil && [connectedDevices objectForKey:address] != nil;
}

+(CBPeripheral *)connectedPeripheralForAddress:(NSString *)address {
    if (connectedDevices == nil || [connectedDevices count] == 0) {
        return nil;
    }
    if (address != nil) {
        return [connectedDevices objectForKey:address];
    }
    // Fallback: return first connected device
    return [[connectedDevices allValues] firstObject];
}

+(void)writeValue:(NSData *) data toAddress:(NSString *)address withDelegate:(NSObject<WriteDataToBleDelegate> *) delegate
{
    @try{
        CBPeripheral *target = [self connectedPeripheralForAddress:address];
        if (target == nil) {
            NSLog(@"No connected peripheral for address: %@", address);
            [delegate didWriteDataToBle:false];
            return;
        }
        writeDataDelegate = delegate;
        toWrite = data;
        currentWriteAddress = address;
        target.delegate = instance;
        [target discoverServices:supportServices];
    }
    @catch(NSException *e){
        NSLog(@"error in writing data to address %@, issue:%@", address, e);
        [writeDataDelegate didWriteDataToBle:false];
    }
}

+(void)writeValue:(NSData *) data withDelegate:(NSObject<WriteDataToBleDelegate> *) delegate
{
    [self writeValue:data toAddress:nil withDelegate:delegate];
}

// Will be called when this module's first listener is added.
-(void)startObserving {
    hasListeners = YES;
}

// Will be called when this module's last listener is removed, or on dealloc.
-(void)stopObserving {
    hasListeners = NO;
}

/**
 * Exports the constants to javascript.
 **/
- (NSDictionary *)constantsToExport
{
    return @{ EVENT_DEVICE_ALREADY_PAIRED: EVENT_DEVICE_ALREADY_PAIRED,
              EVENT_DEVICE_DISCOVER_DONE:EVENT_DEVICE_DISCOVER_DONE,
              EVENT_DEVICE_FOUND:EVENT_DEVICE_FOUND,
              EVENT_CONNECTION_LOST:EVENT_CONNECTION_LOST,
              EVENT_UNABLE_CONNECT:EVENT_UNABLE_CONNECT,
              EVENT_CONNECTED:EVENT_CONNECTED
              };
}
- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

/**
 * Defines the events that can be emitted.
 **/
- (NSArray<NSString *> *)supportedEvents
{
    return @[EVENT_DEVICE_DISCOVER_DONE,
             EVENT_DEVICE_FOUND,
             EVENT_UNABLE_CONNECT,
             EVENT_CONNECTION_LOST,
             EVENT_CONNECTED,
             EVENT_DEVICE_ALREADY_PAIRED];
}


RCT_EXPORT_MODULE(BluetoothManager);


//isBluetoothEnabled
RCT_EXPORT_METHOD(isBluetoothEnabled:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    CBManagerState state = [self.centralManager  state];
    resolve(state == CBManagerStatePoweredOn?@"true":@"false");
}

//enableBluetooth
RCT_EXPORT_METHOD(enableBluetooth:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve(nil);
}
//disableBluetooth
RCT_EXPORT_METHOD(disableBluetooth:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    // Disconnect all devices
    if (connectedDevices && [connectedDevices count] > 0) {
        for (NSString *key in [connectedDevices allKeys]) {
            CBPeripheral *peripheral = [connectedDevices objectForKey:key];
            [self.centralManager cancelPeripheralConnection:peripheral];
        }
        [connectedDevices removeAllObjects];
    }
    resolve(nil);
}

//scanDevices
RCT_EXPORT_METHOD(scanDevices:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    @try{
        if(!self.centralManager || self.centralManager.state!=CBManagerStatePoweredOn){
            reject(@"BLUETOOTH_INVALID_STATE",@"BLUETOOTH_INVALID_STATE",nil);
            return;
        }
        if(self.centralManager.isScanning){
            [self.centralManager stopScan];
        }
        self.scanResolveBlock = resolve;
        self.scanRejectBlock = reject;

        // Include already-connected devices in found list
        if (connectedDevices && [connectedDevices count] > 0) {
            for (NSString *key in connectedDevices) {
                CBPeripheral *p = [connectedDevices objectForKey:key];
                NSDictionary *idAndName = @{@"address":p.identifier.UUIDString, @"name":p.name ? p.name : @""};
                NSDictionary *peripheralStored = @{p.identifier.UUIDString:p};
                if(!self.foundDevices){
                    self.foundDevices = [[NSMutableDictionary alloc] init];
                }
                [self.foundDevices addEntriesFromDictionary:peripheralStored];
                if(hasListeners){
                    [self sendEventWithName:EVENT_DEVICE_FOUND body:@{@"device":idAndName}];
                }
            }
        }

        [self.centralManager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey:@NO}];
        NSLog(@"Scanning started with services.");
        if(timer && timer.isValid){
            [timer invalidate];
            timer = nil;
        }
        timer = [NSTimer scheduledTimerWithTimeInterval:30 target:self selector:@selector(callStop) userInfo:nil repeats:NO];
    
    }
    @catch(NSException *exception){
        NSLog(@"ERROR IN STARTING SCAN %@",exception);
        reject([exception name],[exception name],nil);
    }
}

//stop scan
RCT_EXPORT_METHOD(stopScan:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    [self callStop];
    resolve(nil);
}

//connect(address) — adds to connection pool (up to MAX_CONNECTIONS)
RCT_EXPORT_METHOD(connect:(NSString *)address
                  findEventsWithResolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"Trying to connect....%@",address);
    [self callStop];

    // Check if already connected to this device
    CBPeripheral *existingPeripheral = [connectedDevices objectForKey:address];
    if (existingPeripheral && existingPeripheral.state == CBPeripheralStateConnected) {
        NSLog(@"Already connected to %@", address);
        resolve(nil);
        return;
    }

    // Check connection limit
    if ([connectedDevices count] >= MAX_CONNECTIONS) {
        NSLog(@"Maximum connections (%d) reached", MAX_CONNECTIONS);
        reject(@"MAX_CONNECTIONS_REACHED", @"Maximum number of simultaneous connections reached", nil);
        return;
    }

    CBPeripheral *peripheral = [self.foundDevices objectForKey:address];
    self.connectResolveBlock = resolve;
    self.connectRejectBlock = reject;
    if(peripheral){
        _waitingConnect = address;
        NSLog(@"Trying to connectPeripheral....%@",address);
        [self.centralManager connectPeripheral:peripheral options:nil];
    }else{
        _waitingConnect = address;
        NSLog(@"Scan to find ....%@",address);
        [self.centralManager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey:@NO}];
    }
}

//disconnect(address) — disconnect a specific device, or all if address is nil
RCT_EXPORT_METHOD(disconnect:(NSString *)address
                  withResolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        if (address != nil && [address length] > 0) {
            // Disconnect specific device
            CBPeripheral *peripheral = [connectedDevices objectForKey:address];
            if (peripheral) {
                [self.centralManager cancelPeripheralConnection:peripheral];
                [connectedDevices removeObjectForKey:address];
            }
        } else {
            // Disconnect all
            for (NSString *key in [connectedDevices allKeys]) {
                CBPeripheral *peripheral = [connectedDevices objectForKey:key];
                [self.centralManager cancelPeripheralConnection:peripheral];
            }
            [connectedDevices removeAllObjects];
        }
        resolve(nil);
    }
    @catch(NSException *exception){
        reject([exception name],[exception name],nil);
    }
}

//getConnectedDevices
RCT_EXPORT_METHOD(getConnectedDevices:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSMutableArray *devices = [[NSMutableArray alloc] init];
        for (NSString *key in connectedDevices) {
            CBPeripheral *p = [connectedDevices objectForKey:key];
            if (p) {
                [devices addObject:@{@"address": p.identifier.UUIDString,
                                     @"name": p.name ? p.name : @""}];
            }
        }
        NSError *error = nil;
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:devices options:0 error:&error];
        if (jsonData) {
            NSMutableArray *result = [[NSMutableArray alloc] init];
            for (NSDictionary *d in devices) {
                NSData *itemData = [NSJSONSerialization dataWithJSONObject:d options:0 error:nil];
                NSString *itemStr = [[NSString alloc] initWithData:itemData encoding:NSUTF8StringEncoding];
                [result addObject:itemStr];
            }
            resolve(result);
        } else {
            resolve(@[]);
        }
    }
    @catch(NSException *exception) {
        reject([exception name], [exception name], nil);
    }
}

//unpair(address)
RCT_EXPORT_METHOD(unpair:(NSString *)address
                  withResolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    // Disconnect if connected
    CBPeripheral *peripheral = [connectedDevices objectForKey:address];
    if (peripheral) {
        [self.centralManager cancelPeripheralConnection:peripheral];
        [connectedDevices removeObjectForKey:address];
    }
    resolve(address);
}


-(void)callStop{
    if(self.centralManager.isScanning){
        [self.centralManager stopScan];
        NSMutableArray *devices = [[NSMutableArray alloc] init];
        for(NSString *key in self.foundDevices){
            NSLog(@"insert found devices:%@ =>%@",key,[self.foundDevices objectForKey:key]);
            NSString *name = [self.foundDevices objectForKey:key].name;
            if(!name){
                name = @"";
            }
            [devices addObject:@{@"address":key,@"name":name}];
        }
        NSError *error = nil;
        NSData* jsonData = [NSJSONSerialization dataWithJSONObject:devices options:NSJSONWritingPrettyPrinted error:&error];
        NSString * jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        if(hasListeners){
            [self sendEventWithName:EVENT_DEVICE_DISCOVER_DONE body:@{@"found":jsonStr,@"paired":@"[]"}];
        }
        if(self.scanResolveBlock){
            RCTPromiseResolveBlock rsBlock = self.scanResolveBlock;
            rsBlock(@{@"found":jsonStr,@"paired":@"[]"});
            self.scanResolveBlock = nil;
        }
    }
    if(timer && timer.isValid){
        [timer invalidate];
        timer = nil;
    }
    self.scanRejectBlock = nil;
    self.scanResolveBlock = nil;
}

- (void) initSupportServices
{
    if(!supportServices){
        CBUUID *issc = [CBUUID UUIDWithString: @"49535343-FE7D-4AE5-8FA9-9FAFD205E455"];
        supportServices = [NSArray arrayWithObject:issc];/*ISSC*/
        writeableCharactiscs = @{issc:@"49535343-8841-43F4-A8D4-ECBE34729BB3"};
    }
}

- (CBCentralManager *) centralManager
{
    @synchronized(_centralManager)
    {
        if (!_centralManager)
        {
            if (![CBCentralManager instancesRespondToSelector:@selector(initWithDelegate:queue:options:)])
            {
                self.centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
            }else
            {
                self.centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options: nil];
            }
        }
        if(!instance){
            instance = self;
        }
    }
    [self initSupportServices];
    return _centralManager;
}

/**
 * CBCentralManagerDelegate
 **/
- (void)centralManagerDidUpdateState:(CBCentralManager *)central{
    NSLog(@"%ld",(long)central.state);
}

- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary<NSString *, id> *)advertisementData RSSI:(NSNumber *)RSSI{
    NSLog(@"did discover peripheral: %@",peripheral);
    NSDictionary *idAndName =@{@"address":peripheral.identifier.UUIDString,@"name":peripheral.name?peripheral.name:@""};
    NSDictionary *peripheralStored = @{peripheral.identifier.UUIDString:peripheral};
    if(!self.foundDevices){
        self.foundDevices = [[NSMutableDictionary alloc] init];
    }
    [self.foundDevices addEntriesFromDictionary:peripheralStored];
    if(hasListeners){
        [self sendEventWithName:EVENT_DEVICE_FOUND body:@{@"device":idAndName}];
    }
    if(_waitingConnect && [_waitingConnect isEqualToString: peripheral.identifier.UUIDString]){
        [self.centralManager connectPeripheral:peripheral options:nil];
        [self callStop];
    }
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral{
    NSLog(@"did connected: %@",peripheral);

    // Add to connection pool
    NSString *pId = peripheral.identifier.UUIDString;
    [connectedDevices setObject:peripheral forKey:pId];

    if(_waitingConnect && [_waitingConnect isEqualToString: pId] && self.connectResolveBlock){
        NSLog(@"Predefined the support services, stop to looking up services.");
        self.connectResolveBlock(nil);
        _waitingConnect = nil;
        self.connectRejectBlock = nil;
        self.connectResolveBlock = nil;
    }
    NSLog(@"going to emit EVENT_CONNECTED.");
    if(hasListeners){
        [self sendEventWithName:EVENT_CONNECTED body:@{@"device":@{@"name":peripheral.name?peripheral.name:@"",@"address":peripheral.identifier.UUIDString}}];
    }
}

- (void)centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(nullable NSError *)error{
    NSString *pId = peripheral.identifier.UUIDString;

    // Remove from connection pool
    [connectedDevices removeObjectForKey:pId];

    if(_waitingConnect && [_waitingConnect isEqualToString:pId]){
        if(self.connectRejectBlock){
            RCTPromiseRejectBlock rjBlock = self.connectRejectBlock;
            rjBlock(@"",@"",error);
            self.connectRejectBlock = nil;
            self.connectResolveBlock = nil;
            _waitingConnect=nil;
        }
        if(hasListeners){
            [self sendEventWithName:EVENT_UNABLE_CONNECT body:@{@"name":peripheral.name?peripheral.name:@"",@"address":pId}];
        }
    }else{
        if(hasListeners){
            NSDictionary *body = @{@"device_address": pId, @"device_name": peripheral.name ? peripheral.name : @""};
            [self sendEventWithName:EVENT_CONNECTION_LOST body:body];
        }
    }
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(nullable NSError *)error{
    NSString *pId = peripheral.identifier.UUIDString;

    // Remove from connection pool if present
    [connectedDevices removeObjectForKey:pId];

    if(self.connectRejectBlock){
        RCTPromiseRejectBlock rjBlock = self.connectRejectBlock;
        rjBlock(@"",@"",error);
        self.connectRejectBlock = nil;
        self.connectResolveBlock = nil;
        _waitingConnect = nil;
    }
    if(hasListeners){
        [self sendEventWithName:EVENT_UNABLE_CONNECT body:@{@"name":peripheral.name?peripheral.name:@"",@"address":pId}];
    }
}

/**
 * END OF CBCentralManagerDelegate
 **/

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(nullable NSError *)error{
    if (error){
        NSLog(@"Discover services error: %@-> %@", peripheral.name, [error localizedDescription]);
        return;
    }
    NSLog(@"Discovered services: %@ -> %@",peripheral.name,peripheral.services);
    for (CBService *service in peripheral.services) {
        [peripheral discoverCharacteristics:nil forService:service];
        NSLog(@"Service UUID: %@",service.UUID.UUIDString);
    }
    NSLog(@"Discovering characteristics for %@...",peripheral.name);
    
    if(error && self.connectRejectBlock){
        RCTPromiseRejectBlock rjBlock = self.connectRejectBlock;
         rjBlock(@"",@"",error);
        self.connectRejectBlock = nil;
        self.connectResolveBlock = nil;
        [connectedDevices removeObjectForKey:peripheral.identifier.UUIDString];
    }else
    if(_waitingConnect && _waitingConnect == peripheral.identifier.UUIDString){
        RCTPromiseResolveBlock rsBlock = self.connectResolveBlock;
        rsBlock(peripheral.identifier.UUIDString);
        self.connectRejectBlock = nil;
        self.connectResolveBlock = nil;
        [connectedDevices setObject:peripheral forKey:peripheral.identifier.UUIDString];
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverCharacteristicsForService:(CBService *)service error:(nullable NSError *)error{
    // Find the target peripheral — check if this peripheral is in the connection pool
    CBPeripheral *target = [connectedDevices objectForKey:peripheral.identifier.UUIDString];
    if(toWrite && target
       && [target.identifier.UUIDString isEqualToString:peripheral.identifier.UUIDString]
       && [service.UUID.UUIDString isEqualToString:supportServices[0].UUIDString]){
        if(error){
            NSLog(@"Discover characteristics error:%@",error);
           if(writeDataDelegate)
           {
               [writeDataDelegate didWriteDataToBle:false];
               return;
           }
        }
        for(CBCharacteristic *cc in service.characteristics){
            NSLog(@"Characteristic found: %@ in service: %@" ,cc,service.UUID.UUIDString);
            if([cc.UUID.UUIDString isEqualToString:[writeableCharactiscs objectForKey: supportServices[0]]]){
                @try{
                    [target writeValue:toWrite forCharacteristic:cc type:CBCharacteristicWriteWithoutResponse];
                   if(writeDataDelegate) [writeDataDelegate didWriteDataToBle:true];
                    if(toWrite){
                        NSLog(@"Value wrote: %lu",[toWrite length]);
                    }
                }
                @catch(NSException *e){
                    NSLog(@"ERROR IN WRITE VALUE: %@",e);
                      [writeDataDelegate didWriteDataToBle:false];
                }
            }
        }
    }
    
    if(error){
        NSLog(@"Discover characteristics error:%@",error);
        return;
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didWriteValueForCharacteristic:(CBCharacteristic *)characteristic error:(nullable NSError *)error{
    if(error){
        NSLog(@"Error in writing bluetooth: %@",error);
        if(writeDataDelegate){
            [writeDataDelegate didWriteDataToBle:false];
        }
    }
    
    NSLog(@"Write bluetooth success.");
    if(writeDataDelegate){
        [writeDataDelegate didWriteDataToBle:true];
    }
}
 
@end
