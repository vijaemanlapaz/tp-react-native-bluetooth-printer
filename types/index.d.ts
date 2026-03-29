declare module "tp-react-native-bluetooth-printer" {
  export enum DIRECTION {
    FORWARD = 0,
    BACKWARD = 1,
  }
  export enum DENSITY {
    DNESITY0 = 0,
    DNESITY1 = 1,
    DNESITY2 = 2,
    DNESITY3 = 3,
    DNESITY4 = 4,
    DNESITY5 = 5,
    DNESITY6 = 6,
    DNESITY7 = 7,
    DNESITY8 = 8,
    DNESITY9 = 9,
    DNESITY10 = 10,
    DNESITY11 = 11,
    DNESITY12 = 12,
    DNESITY13 = 13,
    DNESITY14 = 14,
    DNESITY15 = 15,
  }
  export enum TSC_BARCODETYPE {
    BARCODE128 = "128",
    BARCODE128M = "128M",
    EAN128 = "EAN128",
    ITF25 = "25",
    ITF25C = "25C",
    BARCODE39 = "39",
    BARCODE39C = "39C",
    BARCODE39S = "39S",
    BARCODE93 = "93",
    EAN13 = "EAN13",
    EAN13_2 = "EAN13+2",
    EAN13_5 = "EAN13+5",
    EAN8 = "EAN8",
    EAN8_2 = "EAN8+2",
    EAN8_5 = "EAN8+5",
    BARCODABAR = "CODA",
    POST = "POST",
    UPCA = "EAN13",
    UPCA_2 = "EAN13+2",
    UPCA_5 = "EAN13+5",
    UPCE = "EAN13",
    UPCE_2 = "EAN13+2",
    UPCE_5 = "EAN13+5",
    CPOST = "CPOST",
    MSI = "MSI",
    MSIC = "MSIC",
    PLESSEY = "PLESSEY",
    ITF14 = "ITF14",
    EAN14 = "EAN14",
  }
  export enum FONTTYPE {
    FONT_1 = "1",
    FONT_2 = "2",
    FONT_3 = "3",
    FONT_4 = "4",
    FONT_5 = "5",
    FONT_6 = "6",
    FONT_7 = "7",
    FONT_8 = "8",
    SIMPLIFIED_CHINESE = "TSS24.BF2",
    TRADITIONAL_CHINESE = "TST24.BF2",
    KOREAN = "K",
  }
  export enum EEC {
    LEVEL_L = "L",
    LEVEL_M = "M",
    LEVEL_Q = "Q",
    LEVEL_H = "H",
  }
  export enum TSC_ROTATION {
    ROTATION_0 = 0,
    ROTATION_90 = 90,
    ROTATION_180 = 180,
    ROTATION_270 = 270,
  }
  export enum FONTMUL {
    MUL_1 = 1,
    MUL_2 = 2,
    MUL_3 = 3,
    MUL_4 = 4,
    MUL_5 = 5,
    MUL_6 = 6,
    MUL_7 = 7,
    MUL_8 = 8,
    MUL_9 = 9,
    MUL_10 = 10,
  }
  export enum BITMAP_MODE {
    OVERWRITE = 0,
    OR = 1,
    XOR = 2,
  }
  export enum PRINT_SPEED {
    SPEED1DIV5 = 1,
    SPEED2 = 2,
    SPEED3 = 3,
    SPEED4 = 4,
  }
  export enum TEAR {
    ON = "ON",
    OFF = "OFF",
  }
  export enum READABLE {
    DISABLE = 0,
    ENABLE = 1,
  }
  export enum ERROR_CORRECTION {
    L = 1,
    M = 0,
    Q = 3,
    H = 2,
  }
  export enum BARCODETYPE {
    UPC_A = 65, //11<=n<=12
    UPC_E = 66, //11<=n<=12
    JAN13 = 67, //12<=n<=12
    JAN8 = 68, //7<=n<=8
    CODE39 = 69, //1<=n<=255
    ITF = 70, //1<=n<=255(even numbers)
    CODABAR = 71, //1<=n<=255
    CODE93 = 72, //1<=n<=255
    CODE128 = 73, //2<=n<=255
  }
  export enum ESC_ROTATION {
    OFF = 0,
    ON = 1,
  }
  export enum ALIGN {
    LEFT = 0,
    CENTER = 1,
    RIGHT = 2,
  }
  export enum PAGE_WIDTH {
    WIDTH_58 = 384,
    WIDTH_80 = 576,
  }
  export enum MODE {
    DISABLE = 0,
    ENABLE = 1,
  }

  export type BluetoothDevice = {
    name: string;
    address: string;
  };

  export type ScannedBluetoothDevices = {
    paired: BluetoothDevice[];
    found: BluetoothDevice[];
  };

  export type PrintTextOptions = {
    encoding?: string;
    codepage?: number;
    widthtimes?: number;
    heigthtimes?: number;
    fonttype?: number;
  };

  export type PrintPictureOptions = {
    width?: number;
    height?: number;
    left?: number;
  };

  export type PrintLabelOptions = {
    width: number;
    height: number;
    gap?: number;
    speed?: number | typeof PRINT_SPEED;
    tear?: string | typeof TEAR;
    text?: any[];
    qrcode?: any[];
    barcode?: any[];
    image?: any[];
    reverse?: any[];
    direction?: number | typeof DIRECTION;
    density?: number | typeof DENSITY;
    reference?: any[];
    sound?: number | typeof READABLE;
    home?: number | typeof READABLE;
    /** Optional device address to target a specific connected printer */
    address?: string;
  };

  /**
   * Connection event payload emitted for device-specific events.
   * Events now include `device_address` to identify which printer triggered the event.
   */
  export type ConnectionEventPayload = {
    device_name?: string;
    device_address?: string;
  };

  /** Maximum number of simultaneous Bluetooth connections allowed */
  export const MAX_CONNECTIONS: 7;

  export class BluetoothManager {
    static enableBluetooth():
      | void
      | PromiseLike<void>
      | PromiseLike<BluetoothDevice[]>;
    static disableBluetooth(): boolean | PromiseLike<boolean>;
    static isBluetoothEnabled(): boolean | PromiseLike<boolean>;
    static scanDevices():
      | ScannedBluetoothDevices
      | PromiseLike<ScannedBluetoothDevices>;

    /**
     * Connect to a Bluetooth device. Multiple devices can be connected simultaneously
     * (up to MAX_CONNECTIONS). Resolves with device info including name and address.
     */
    static connect(
      address: string
    ): ConnectionEventPayload | PromiseLike<ConnectionEventPayload>;

    /**
     * Disconnect from a specific device by address, or disconnect all devices if
     * address is null/undefined.
     */
    static disconnect(address?: string | null): void | PromiseLike<void>;

    /**
     * Get all currently connected devices.
     */
    static getConnectedDevices():
      | BluetoothDevice[]
      | PromiseLike<BluetoothDevice[]>;

    static unpair(address: string): string | PromiseLike<string>;
  }

  export class BluetoothEscposPrinter {
    /**
     * Initialize the printer. Optionally target a specific printer by address.
     */
    static printerInit(
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Print and feed paper. Optionally target a specific printer by address.
     */
    static printAndFeed(
      feed: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set left space. Optionally target a specific printer by address.
     */
    static printerLeftSpace(
      space: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set line space. Optionally target a specific printer by address.
     */
    static printerLineSpace(
      space: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set underline mode. Optionally target a specific printer by address.
     */
    static printerUnderLine(
      line: number | typeof READABLE,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set text alignment. Optionally target a specific printer by address.
     */
    static printerAlign(
      align: number | typeof ALIGN,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Print text. Optionally target a specific printer by address.
     */
    static printText(
      text: string,
      options?: PrintTextOptions,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Print columnar text. Optionally target a specific printer by address.
     */
    static printColumn(
      columnWidths: number[],
      columnAligns: number[] | (typeof ALIGN)[],
      columnTexts: string[],
      options?: PrintTextOptions,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set device width. Optionally target a specific printer by address
     * for per-device width tracking.
     */
    static setWidth(
      width: number | typeof PAGE_WIDTH,
      address?: string | null
    ): void | PromiseLike<void>;

    /**
     * Print a picture. Optionally target a specific printer by address.
     */
    static printPic(
      base64Image: string,
      options?: PrintPictureOptions,
      address?: string | null
    ): void | PromiseLike<void>;

    /**
     * Cut paper. Optionally target a specific printer by address.
     */
    static cutLine(
      line: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Self test. Optionally target a specific printer by address.
     */
    static selfTest(
      address?: string | null,
      callback?: (result: boolean) => void
    ): void | PromiseLike<void>;

    /**
     * Rotate 90°. Optionally target a specific printer by address.
     */
    static rotate(
      rotate: number | typeof ESC_ROTATION,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Set bold weight. Optionally target a specific printer by address.
     */
    static setBold(
      weight: number | typeof ESC_ROTATION,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Print QR code. Optionally target a specific printer by address.
     */
    static printQRCode(
      content: string,
      size: number,
      correctionLevel: number | typeof ERROR_CORRECTION,
      leftPadding?: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;

    /**
     * Print barcode. Optionally target a specific printer by address.
     */
    static printBarCode(
      content: string,
      barcodeType: number | typeof BARCODETYPE,
      width: number,
      height: number,
      fontType: number | typeof FONTTYPE,
      fontPosition: number,
      address?: string | null
    ): void | string | PromiseLike<void> | PromiseLike<string>;
  }

  export class BluetoothTscPrinter {
    /**
     * Print a TSC label. The options map now supports an optional `address` field
     * to target a specific connected printer.
     */
    static printLabel(
      options: PrintLabelOptions
    ): void | string | PromiseLike<void> | PromiseLike<string>;
  }
}
