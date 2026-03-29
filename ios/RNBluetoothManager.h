//
//  RNBluetoothManager.h
//  RNBluetoothEscposPrinter
//
 
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <CoreBluetooth/CoreBluetooth.h>

/** Maximum number of simultaneous BLE connections allowed */
#define MAX_CONNECTIONS 7

@protocol WriteDataToBleDelegate <NSObject>
@required
- (void) didWriteDataToBle: (BOOL)success;
@end

@interface RNBluetoothManager <CBCentralManagerDelegate,CBPeripheralDelegate> : RCTEventEmitter <RCTBridgeModule>
@property (strong, nonatomic) CBCentralManager      *centralManager;
@property (nonatomic,copy) RCTPromiseResolveBlock scanResolveBlock;
@property (nonatomic,copy) RCTPromiseRejectBlock scanRejectBlock;
@property (strong,nonatomic) NSMutableDictionary <NSString *,CBPeripheral *> *foundDevices;
@property (strong,nonatomic) NSString *waitingConnect;
@property (nonatomic,copy) RCTPromiseResolveBlock connectResolveBlock;
@property (nonatomic,copy) RCTPromiseRejectBlock connectRejectBlock;

/** Connection pool: maps peripheral UUID string → CBPeripheral */
@property (strong, nonatomic) NSMutableDictionary<NSString *, CBPeripheral *> *connectedDevices;

/**
 * Write data to a specific connected peripheral by address.
 * If address is nil, falls back to the first connected device (backward compatibility).
 */
+(void)writeValue:(NSData *) data toAddress:(NSString *)address withDelegate:(NSObject<WriteDataToBleDelegate> *) delegate;

/** Backward-compatible write to the first connected device */
+(void)writeValue:(NSData *) data withDelegate:(NSObject<WriteDataToBleDelegate> *) delegate;

/** Check if any device is connected */
+(Boolean)isConnected;

/** Check if a specific device is connected by address */
+(Boolean)isConnectedToAddress:(NSString *)address;

/** Get the connected peripheral for an address (nil = first connected) */
+(CBPeripheral *)connectedPeripheralForAddress:(NSString *)address;

-(void)initSupportServices;
-(void)callStop;
@end
