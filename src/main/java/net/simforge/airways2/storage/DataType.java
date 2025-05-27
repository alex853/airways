package net.simforge.airways2.storage;

public enum DataType {
    Signed32bit, // just as usual 'int'
    Unsigned24bit, // 3 bytes unsigned integer
    Unsigned16bit, // 2 bytes unsigned integer
    Unsigned8bit, // 1 byte unsiged integer, from 0 to 255
    Float,
    LatLong24bit, // accuracy 0.0001, this means 11 meters step on equator
    LatLong16bit, // accuracy 0.01, this means 1.1 kilometer step on equator
    PlainString, // default length is 20 bytes, max length is 254 bytes
//    PackedTo6BitsString
}
