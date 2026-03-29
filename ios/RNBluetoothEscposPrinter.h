
#import <React/RCTBridgeModule.h>
#import "RNBluetoothManager.h";

@interface RNBluetoothEscposPrinter : NSObject <RCTBridgeModule,WriteDataToBleDelegate>

@property (nonatomic,assign) NSInteger deviceWidth;
/** Per-device width tracking */
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSNumber *> *deviceWidths;

-(void) textPrint:(NSString *) text
       inEncoding:(NSString *) encoding
     withCodePage:(NSInteger) codePage
       widthTimes:(NSInteger) widthTimes
      heightTimes:(NSInteger) heightTimes
         fontType:(NSInteger) fontType
        toAddress:(NSString *) address
         delegate:(NSObject<WriteDataToBleDelegate> *) delegate;

/** Backward-compatible version without address */
-(void) textPrint:(NSString *) text
       inEncoding:(NSString *) encoding
     withCodePage:(NSInteger) codePage
       widthTimes:(NSInteger) widthTimes
      heightTimes:(NSInteger) heightTimes
         fontType:(NSInteger) fontType
         delegate:(NSObject<WriteDataToBleDelegate> *) delegate;
@end
  
