package net.simforge.airways2.storage;

public enum DataType {
    Signed32bit, // just as usual 'int'
    Signed40bit, // 5 bytes signed integer, from -549 755 813 888 to 549 755 813 887 (money: balances and amounts), read and written by getAsLong and setLong
    Unsigned24bit, // 3 bytes unsigned integer
    Unsigned16bit, // 2 bytes unsigned integer
    Unsigned8bit, // 1 byte unsiged integer, from 0 to 255
    Float,
    LatLong24bit, // accuracy 0.0001, this means 11 meters step on equator
    LatLong16bit, // accuracy 0.01, this means 1.1 kilometer step on equator
    Tenths8bit, // a decimal with one digit after the point in one byte: from 0.0 to 25.5, accuracy 0.1; it is stored as an integer from 0 to 255 (3.4 is 34)
    Float16bit, // half precision float in two bytes (IEEE 754 binary16): from -65504 to 65504, the accuracy gets worse as the value grows, 1 for values from 1024 to 2048
    PlainString, // default length is 20 bytes, max length is 254 bytes
//    PackedTo6BitsString
}
