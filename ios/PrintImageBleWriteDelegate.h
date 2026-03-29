//
//  PrintImageBleWriteDelegate.h
//  RNBluetoothEscposPrinter
//
 
#import <React/RCTBridgeModule.h>
#import "RNBluetoothManager.h"
#import "RNBluetoothEscposPrinter.h"
@interface PrintImageBleWriteDelegate :NSObject<WriteDataToBleDelegate>
@property NSData *toPrint;
@property NSInteger width;
@property NSInteger now;
@property NSInteger left;
@property RNBluetoothManager *printer;
@property RCTPromiseRejectBlock pendingReject;
@property RCTPromiseResolveBlock pendingResolve;
/** Optional target device address for multi-printer routing */
@property (nonatomic, strong) NSString *targetAddress;
-(void) print;
@end
